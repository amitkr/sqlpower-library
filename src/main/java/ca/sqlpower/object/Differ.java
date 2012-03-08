package ca.sqlpower.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import ca.sqlpower.dao.PersistedSPOProperty;
import ca.sqlpower.dao.PersistedSPObject;
import ca.sqlpower.dao.PersisterUtils;
import ca.sqlpower.dao.SPPersistenceException;
import ca.sqlpower.dao.SPPersister;
import ca.sqlpower.diff.DiffChunk;
import ca.sqlpower.diff.DiffChunkTreeNode;
import ca.sqlpower.diff.DiffInfo;
import ca.sqlpower.diff.DiffType;
import ca.sqlpower.diff.PropertyChange;
import ca.sqlpower.sqlobject.SQLObjectException;

/**
 * XXX This is a copy of the Differ class from the enterprise library. Some methods for calculating diffs has been removed
 * as the classes required for them are not in this library. We should re-evaluate the classes available in the base library
 * and the enterprise library.
 * <p> 
 * This class may be used in conjunction with a {@link RevisionPersister} to calculate diffs 
 * between two revisions in the {@link RevisionPersister}, and persist them to another {@link SPPersister},
 * or grab them as lists from field accessors.
 * 
 * <p>While this class can take either any {@link RevisionPersister} and two revisions, or lists of persisted objects/properties,
 * it's original intention is to take revisions from the {@link JCRPersister}, and persist them to the old revision to update it.
 *
 */

public class Differ implements SPPersister {
    
    private static final Logger logger = Logger.getLogger(Differ.class);
    
    /**
     * Used by the {@link calcDiff()} method that takes a persister, to temporarily
     * store object persist calls that will be put into the old or new object revision lists. 
     */
    private List<PersistedSPObject> spObjectListToPopulate;
    /**
     * Used by the {@link calcDiff()} method that takes a persister, to temporarily
     * store property persist calls that will be put into the old or new property revision lists. 
     */
    private List<PersistedSPOProperty> spoPropertyListToPopulate;
    
    /**
     * A map of the old objects, stored by their uuids. Kept as a field to be used
     * by other classes to avoid reconstructing a hash map.
     */  
    private HashMap<String, PersistedSPObject> oldObjectMap;
    
    /**
     * A map of the new objects, stored by their uuids. Kept as a field to be used
     * by other classes to avoid reconstructing a hash map.
     */  
    private HashMap<String, PersistedSPObject> newObjectMap;
    
    /**
     * A map of the old object properties, stored by their uuids and property names. 
     * Kept as a field to be used by other classes to avoid reconstructing a hash map.
     */   
    private HashMap<String, PersistedSPOProperty> oldPropertyMap;  
    
    /**
     * A map of the new object properties, stored by their uuids and property names. 
     * Kept as a field to be used by other classes to avoid reconstructing a hash map.
     */  
    private HashMap<String, PersistedSPOProperty> newPropertyMap;

    /**
     * The persist calls generated by this differ, and subsequently stored in the static cache.
     */
    private DifferPersistCalls persistCalls;
    
    /**
     * This is used to keep track of which objects were moved and all their descendants
     * so that the property loop knows to add the properties of those objects in the diff.
     * ie: If you move a column, that is a remove and add call of the column, and add calls
     * of all the column's descendants. All the properties of all those objects must be persisted.
     */
    private Set<String> needToAddProperties = new HashSet<String>();
    
    /**
     * This can be created if needed by getObjectTree(), but is not done so by default.
     * It will map object UUIDs to PersistedObjectTreeNode objects.
     */
    private HashMap<String, PersistedObjectTreeNode> treeMap = null;
    
    /**
     * Tracks if this differ has already been used to calculate the difference between
     * two workspaces. Due to the number of lists and maps stored as class level variables
     * each instance of a differ object can only calculate a diff once.
     */
    private boolean diffCalculated = false;
    
    /**
     * A container class to store the persist calls of this differ.
     * Created so that it may be stored in the cache, instead of the entire Differ object.
     */
    private class DifferPersistCalls {
     
        /**
         * A list of object addition persist calls will be stored here by the
         * {@link calcDiff()} methods to later be accessed, or persisted by {@link persistTo()}.
         */
        protected final List<PersistedSPObject> persistedSPOsToAdd;
        /**
         * A list of object removal persist calls will be stored here by the
         * {@link calcDiff()} methods to later be accessed, or persisted by {@link persistTo()}.
         */    
        protected final List<PersistedSPObject> persistedSPOsToRemove;
        /**
         * A list of property persist calls will be stored here by the
         * {@link calcDiff()} methods to later be accessed, or persisted by {@link persistTo()}.
         */    
        protected final List<PersistedSPOProperty> propertyDiffPersists;
        
        protected DifferPersistCalls() {
            persistedSPOsToAdd = new ArrayList<PersistedSPObject>();
            persistedSPOsToRemove = new ArrayList<PersistedSPObject>();
            propertyDiffPersists = new ArrayList<PersistedSPOProperty>();
            
        }       
    } 
    
    /**
     * This comparator will define an ordering for objects such that objects with less
     * parents in the list given upon construction (objectsToAdd) come before objects with more.
     * In case of ties, which there will be plenty of, the ordering is dependant on indices.
     * 
     * This is to sort a list such that no child will come before its parent, and that
     * siblings will be in ascending index order.
     * 
     * A result of 0 is not consistent with objects being equal to eachother.
     * Therefore, this comparator should only be used for sorting, as intended.
     */
    private class PersistedObjectComparator implements Comparator<PersistedSPObject> {
        
        private HashSet<String> uuidsToAdd = new HashSet<String>();
        /**
         * Map for storing results of the getDepth() method.
         */
        private HashMap<String, Integer> depthMap = new HashMap<String, Integer>();
        
        public PersistedObjectComparator(List<PersistedSPObject> objectsToAdd) {            
            for (PersistedSPObject o : persistCalls.persistedSPOsToAdd) {
                uuidsToAdd.add(o.getUUID());
            }
        }
        
        /**
         * Objects that have less depth/parents come before objects that have more.
         * In case of a tie, objects with a lower index come first.
         */
        public int compare(PersistedSPObject o1, PersistedSPObject o2) {
            if (getDepth(o1) < getDepth(o2)) return -1;
            else if (getDepth(o1) > getDepth(o2)) return 1;
            else return o1.getIndex() - o2.getIndex();
        }
        
        /**
         * This will determine how many ancestors that are in the added objects list
         * that the given object has, and return it. It will also store the result
         * of the given object, and the depths of the ancestors so that they may
         * be looked up for subsequent calls to this method, instead of recalculating.
         */
        private int getDepth(PersistedSPObject o) {
            if (!depthMap.containsKey(o.getUUID())) {
                if (uuidsToAdd.contains(o.getParentUUID())) {
                    depthMap.put(o.getUUID(), getDepth(newObjectMap.get(o.getParentUUID())) + 1);
                } else {
                    depthMap.put(o.getUUID(), 0);
                }
            }
            return depthMap.get(o.getUUID());
        }
    };
    
    /**
     * A simple node object for constructing trees.
     */
    private static class PersistedObjectTreeNode {
                
        final private PersistedSPObject object;
        final private List<PersistedObjectTreeNode> children = new LinkedList<PersistedObjectTreeNode>();
        
        public PersistedObjectTreeNode(PersistedSPObject object) {
            this.object = object;
        }
        
        private void addNode(PersistedObjectTreeNode child) {
            children.add(child);
        }
        
    }
    
    public Differ() {
        
        persistCalls = new DifferPersistCalls();
        
    }

    /**
     * Calculates the lists of {@link PersistedSPObjects} that need to be added/removed
     * to/from the old list to make it the same as the new list.
     * 
     * <p>HashMaps are used to map the both the old and new objects and properties,
     * and using a set of all these map keys, each object/property can be looked up
     * in both the old and new revision to see if they exist and/or have been changed.
     * This is done in private methods {@link calcObjectDiff} and {@link calcPropertyDiff}.
     * 
     * <p>Diffs are stored in list-of-persist-calls fields and can be retrieved by 
     * {@link getPersistedSPOsToAdd()}, {@link getPersistedSPOsToRemove()}, and {@link getPropertyDiffPersists()}. 
     */    
    public synchronized void calcDiff(List<PersistedSPObject> oldPersistedSPOs, 
            List<PersistedSPObject> newPersistedSPOs,
            List<PersistedSPOProperty> oldPersistedSPOPs,
            List<PersistedSPOProperty> newPersistedSPOPs) {
        
        if (diffCalculated) throw new IllegalStateException("This differ has already calculated its diff. " +
        		"Calling this method again will cause the previous diff to enter an invalid state.");
        
        diffCalculated = true;
        
        oldObjectMap = makeObjectHashMap(oldPersistedSPOs);
        newObjectMap = makeObjectHashMap(newPersistedSPOs);
        
        oldPropertyMap = makePropertyHashMap(oldPersistedSPOPs);
        newPropertyMap = makePropertyHashMap(newPersistedSPOPs);
        
        HashSet<String> objectKeys = new HashSet<String>();
        objectKeys.addAll(oldObjectMap.keySet());
        objectKeys.addAll(newObjectMap.keySet());
        
        HashSet<String> propertyKeys = new HashSet<String>();
        propertyKeys.addAll(oldPropertyMap.keySet());
        propertyKeys.addAll(newPropertyMap.keySet());
        
        calcObjectDiff(oldObjectMap, newObjectMap, objectKeys);
        calcPropertyDiff(oldPersistedSPOPs, newPersistedSPOPs, 
                oldPropertyMap, newPropertyMap, propertyKeys); 
        
        if (logger.isDebugEnabled()) {
        	logger.debug("Differ found " + oldPersistedSPOs.size() + " objects in old revision, " + newPersistedSPOs.size() + " objects in new revision");
        	logger.debug("\t" + persistCalls.persistedSPOsToAdd.size() + " objects must be added, and " + persistCalls.persistedSPOsToRemove.size() + " must be removed");
        }
        
    }
    
    /**
     * Constructs a hash map of {@link PersistedSPObject} types, using their uuids as keys.
     * This is used by {@link calcDiff()} to construct {@link oldObjectMap} and {@link newObjectMap},
     * which are later used to calculate the diff.
     */
    private HashMap<String, PersistedSPObject> makeObjectHashMap(List<PersistedSPObject> objects) {
        
        HashMap<String, PersistedSPObject> map = new HashMap<String, PersistedSPObject>();
        
        for (int i = 0; i < objects.size(); i++) {
            PersistedSPObject object = objects.get(i);
            map.put(object.getUUID(), object);
        }
        
        return map;
        
    }
    
    /**
     * Constructs a hash map of {@link PersistedSPOProperty} types, using their uuids concatenated with their propertyName, as keys.
     * This is used by {@link calcDiff()} to construct {@link oldPropertyMap} and {@link newPropertyMap},
     * which are later used to calculate the diff.
     */
    private HashMap<String, PersistedSPOProperty> makePropertyHashMap(List<PersistedSPOProperty> properties) {
        HashMap<String, PersistedSPOProperty> map = new HashMap<String, PersistedSPOProperty>();
        
        for (int i = 0; i < properties.size(); i++) {
            PersistedSPOProperty property = properties.get(i);
            map.put(property.getUUID() + property.getPropertyName(), property);
        }
        
        return map;
    }
    
    /**
     * Will return the PersistedObjectTreeNode with the given uuid if it exists.
     * If the object tree has not be made yet, it will be made.
     */
    private PersistedObjectTreeNode getTreeNode(String uuid) {
        if (treeMap == null) createObjectTreeMap();
        return treeMap.get(uuid);
    }
    
    /**
     * Creates the object tree, and maps each node using the treeMap field.
     */
    private void createObjectTreeMap() {
        treeMap = new HashMap<String, PersistedObjectTreeNode>();
        PersistedSPObject root = newObjectMap.values().iterator().next();
        while (root.getParentUUID() != null && !root.getParentUUID().equals("") && 
        		newObjectMap.get(root.getParentUUID()) != null) {
            logger.debug(root.getParentUUID());
            root = newObjectMap.get(root.getParentUUID());
        }
        treeMap.put(root.getUUID(), new PersistedObjectTreeNode(root));
        for (PersistedSPObject o : newObjectMap.values()) {
            addObjectToTree(o);
        }
    }

    /**
     * Adds the given object to the tree. If it can find the object's
     * parent in the tree, it will add it as a node under that node.
     * If not, it will travel up the the newObjectMap to determine ancestry,
     * and find an ancestor of the object in the tree. It will then add all ancestors
     * of the object as well as the object under the first ancestor in the tree.
     * If the object is already in the tree, it will simply return.
     */
    private void addObjectToTree(PersistedSPObject object) {
        List<PersistedSPObject> ancestors = new LinkedList<PersistedSPObject>();
        PersistedSPObject ancestor = object;

        while (ancestor != null && !treeMap.containsKey(ancestor.getUUID())) {
            ancestors.add(0, ancestor);
            ancestor = newObjectMap.get(ancestor.getParentUUID());
        }

        for (PersistedSPObject o : ancestors) {
            PersistedObjectTreeNode node = new PersistedObjectTreeNode(o);
            if (treeMap.get(o.getParentUUID()) != null) {
            	treeMap.get(o.getParentUUID()).addNode(node);
            }
            treeMap.put(o.getUUID(), node);
        }
    }
    
    /**
     * This method is used by the class to find the diffs between the {@link PersistedSPOBjects}.
     * It goes through the set of uuids that belong to the old and new revision objects, 
     * and determines which were added and which were removed using the object maps.
     */
    private void calcObjectDiff(
            HashMap<String, PersistedSPObject> oldObjectMap,
            HashMap<String, PersistedSPObject> newObjectMap,
            HashSet<String> objectKeys) {           
        
        Iterator<String> keyIterator = objectKeys.iterator();
        HashMap<String, PersistedSPObject> objectsToRemove = new HashMap<String, PersistedSPObject>();
        
        for (int i = 0; i < objectKeys.size(); i++) {
            
            String uuid = keyIterator.next();
            
            PersistedSPObject oldObject = oldObjectMap.get(uuid);
            PersistedSPObject newObject = newObjectMap.get(uuid);
            
            if (oldObject == null) {
                
                //Can be added earlier because the parent was moved. Happens
                //when a child is added to an object that is moving in the
                //same transaction.
                if (!persistCalls.persistedSPOsToAdd.contains(newObject)) {
                    persistCalls.persistedSPOsToAdd.add(newObject);
                }
                
            } else if (newObject == null) {
                
                objectsToRemove.put(oldObject.getUUID(), oldObject);
                
            } else if (!oldObject.equals(newObject)) {
                
                persistCalls.persistedSPOsToRemove.add(oldObject);                
                                
                // Add the moved object and all its descendants.
                // Flag all these objects to have their properties persisted.
                // This is necessary for roll back to work correctly or else
                // the children below the descendant object will not be re-added
                Set<String> descendants = new HashSet<String>();
                getObjectsRecursively(newObject, descendants);                
                for (String descendant : descendants) {
                    if (!persistCalls.persistedSPOsToAdd.contains(newObjectMap.get(descendant))) {
                        persistCalls.persistedSPOsToAdd.add(newObjectMap.get(descendant));
                    }
                }
                needToAddProperties.addAll(descendants);
            }
        }                      
        
        keyIterator = objectsToRemove.keySet().iterator();
        for (int i = 0; i < objectsToRemove.size(); i++) {            
            String uuid = keyIterator.next();
            if (!objectsToRemove.containsKey(objectsToRemove.get(uuid).getParentUUID())) {
                persistCalls.persistedSPOsToRemove.add(objectsToRemove.get(uuid));
            }
                        
        }        
        
    }
    
    /**
     * Adds the uuid of the given object and all its descendants to the given Set of strings,
     * using the object tree (will be constructed if it does not exist).
     */
    private void getObjectsRecursively(PersistedSPObject object, Set<String> descendants) {
        descendants.add(object.getUUID());
        PersistedObjectTreeNode node = getTreeNode(object.getUUID());        
        for (PersistedObjectTreeNode child : node.children) {            
            getObjectsRecursively(child.object, descendants);
        }
    }
    
    /**
     * This method determines the persist calls required to diff the old and new properties.
     * It goes through the set of uuids of the old and new objects, and determines what 
     * needs to be added, removed, or changed using the property maps. The new object map
     * is required in the case of no corresponding new property, to determine if the old
     * property was either changed to null, or the object it was a property of was removed.
     */
    private void calcPropertyDiff(List<PersistedSPOProperty> oldPersistedSPOPs,
            List<PersistedSPOProperty> newPersistedSPOPs,
            HashMap<String, PersistedSPOProperty> oldPropertyMap,
            HashMap<String, PersistedSPOProperty> newPropertyMap,
            HashSet<String> propertyKeys) { 
        
        // Iterate through all the pairs of properties, and determine changes.
        
        Iterator<String> keyIterator = propertyKeys.iterator();
        
        for (int i = 0; i < propertyKeys.size(); i++) {
            
            String key = keyIterator.next();
            PersistedSPOProperty oldProperty = oldPropertyMap.get(key);
            PersistedSPOProperty newProperty = newPropertyMap.get(key);
            
            if (oldProperty == null) {     
                
                // A new property was added, or the old one was changed to a non-null value.
                
                persistCalls.propertyDiffPersists.add(new PersistedSPOProperty(
                        newProperty.getUUID(), newProperty.getPropertyName(), 
                        newProperty.getDataType(), null, newProperty.getNewValue(), true));
                
            } else if (newProperty == null) {
                      
                // The property was either changed to null, or the object was deleted.              
                
                if (newObjectMap.containsKey(oldProperty.getUUID())) {
                    
                    // The corresponding object still exists, so the property was changed to null.
                 
                    persistCalls.propertyDiffPersists.add(new PersistedSPOProperty(
                            oldProperty.getUUID(), oldProperty.getPropertyName(),
                            oldProperty.getDataType(), oldProperty.getNewValue(), null, true));
                }
                 
            } else if (!oldProperty.equals(newProperty)) {
                
                // A normal property change
                
                persistCalls.propertyDiffPersists.add(new PersistedSPOProperty(
                        oldProperty.getUUID(), oldProperty.getPropertyName(),
                        oldProperty.getDataType(), oldProperty.getNewValue(), 
                        newProperty.getNewValue(), true));
                
            } else if (needToAddProperties.contains(newProperty.getUUID())) {                             
                persistCalls.propertyDiffPersists.add(new PersistedSPOProperty(
                        newProperty.getUUID(), newProperty.getPropertyName(),
                        newProperty.getDataType(), newProperty.getNewValue(), 
                        newProperty.getNewValue(), true)); 
            }
        }
    }

    public List<PersistedSPObject> getPersistedSPOsToAdd() {
        return persistCalls.persistedSPOsToAdd;
    }
    
    public List<PersistedSPObject> getPersistedSPOsToRemove() {
        return persistCalls.persistedSPOsToRemove;
    }
    
    public List<PersistedSPOProperty> getPropertyDiffPersists() {
        return persistCalls.propertyDiffPersists;
    }
    
    public void begin() {
        // don't need to do anything
    }
    
    public void commit() {
        // don't need to do anything
    }

    public void persistObject(String parentUUID, String type, String uuid,
            int index) throws SPPersistenceException {
        
        spObjectListToPopulate.add(new PersistedSPObject(parentUUID, type, uuid, index));
        
    }

    public void persistProperty(String uuid, String propertyName,
            DataType propertyType, Object oldValue, Object newValue)
            throws SPPersistenceException {
        
        spoPropertyListToPopulate.add(new PersistedSPOProperty(uuid, propertyName, 
                propertyType, newValue, newValue, false));
        
    }

    public void persistProperty(String uuid, String propertyName,
            DataType propertyType, Object newValue)
            throws SPPersistenceException {
        
        spoPropertyListToPopulate.add(new PersistedSPOProperty(uuid, propertyName, 
                propertyType, newValue, newValue, false));
        
    }

    public void removeObject(String parentUUID, String uuid)
            throws SPPersistenceException {
        
        throw new IllegalStateException("JCR Persistor is wanting to remove objects " +
                "when it is creating revision.");
        
    }

    public void rollback() {
        logger.error("JCR Persistor rolled back when creating revision.");
    }
    
    public void persistTo(SPPersister p) throws SPPersistenceException {
        persistTo(p, true);
    }
    
    /**
     * Persist the calculated diffs to a {@link SPPersister}. These persist calls
     * assume that the {@link calcDiff()} method has been called and the
     * {@link persistCalls.persistedSPOsToAdd}, {@link persistCalls.persistedSPOsToRemove}, and {@link persistCalls.propertyDiffPersists}
     * lists have been populated as a result of that.
     * 
     * <p>The persister should persist these calls to the old revision, and the calls will update it to the new one.
     * 
     * @param p The {@link SPPersister} to receive the calls.
     * @param conditional If true, the Differ will persist only the new value of properties.
     * @throws SPPersistenceException 
     */
    public void persistTo(SPPersister p, boolean justNew) throws SPPersistenceException {
        
        PersistedSPObject object;
        
        p.begin();
        
        // Remove the old objects first because if it was not deleted,
        // but moved somewhere else, it would be added and there would exist
        // two objects with the same UUID temporarily, which might confuse/break the persister.

        for (int i = 0; i < persistCalls.persistedSPOsToRemove.size(); i++) {
            object = persistCalls.persistedSPOsToRemove.get(i);
            p.removeObject(object.getParentUUID(), object.getUUID());
        }  

        for (int i = 0; i < persistCalls.persistedSPOsToAdd.size(); i++) {
            object = persistCalls.persistedSPOsToAdd.get(i);
            p.persistObject(object.getParentUUID(), object.getType(), 
                    object.getUUID(), object.getIndex());
        }          

        for (int i = 0; i < persistCalls.propertyDiffPersists.size(); i++) {
            PersistedSPOProperty property = persistCalls.propertyDiffPersists.get(i);
            if (justNew) {
                p.persistProperty(property.getUUID(), property.getPropertyName(), 
                    property.getDataType(), property.getNewValue());
            } else {
                p.persistProperty(property.getUUID(), property.getPropertyName(), 
                        property.getDataType(), property.getOldValue(), property.getNewValue());
            }
        }

        p.commit();
        
    }
    
    /**
     * This method will sort the Differ's persisted object list so that
     * no parents come after their children in the list. It will also make
     * sure that siblings that are being added are added in ascending
     * order of their indices, so as not to screw up the index property in the JCR.
     * 
     * This does not need to be called when persisting to a client because
     * the client already sorts incoming json (more thoroughly).
     */
    public void sortPersistedObjects() {
        Collections.sort(persistCalls.persistedSPOsToAdd, 
                new PersistedObjectComparator(persistCalls.persistedSPOsToAdd));
    }
    
    public HashMap<String, PersistedSPOProperty> getOldPropertyMap() {
        return oldPropertyMap;
    }
    
    /**
     * This will return a property from the old workspace that has been loaded into the Differ.
     * 
     * @param uuid The uuid of the property's object.
     * @param pName The name of the property
     * @return The property value, or null if either the object or the property type are not found. 
     */
    public Object getOldPropertyValue(String uuid, String pName) {
        return oldPropertyMap.get(uuid + pName).getNewValue();
    }
    
    /**
     * This will return a property from the new workspace that has been loaded into the Differ.
     * 
     * @param uuid The uuid of the property's object.
     * @param pName The name of the property
     * @return The property value, or null if either the object or the property type are not found. 
     */
    public Object getNewPropertyValue(String uuid, String pName) {
        return newPropertyMap.get(uuid + pName).getNewValue();
    }
    
    /**
     * Allows a root object to be omitted from being persisted. Currently used as a hack
     * in ArchitectProjectResource to avoid persisting the SQLObjectRoot of a workspace.
     * This method will look for the root object with the given parent workspace uuid
     * in the objects it plans to persist (not remove). It will also remove its properties
     * from the property diffs to be persisted .
     * 
     * @param workspaceUUID The parent uuid of the root object
     * @param newRootUUID If not a null string, the children of the root object will have
     * their parent UUIDs changed to this value.
     * @return A boolean indicating whether the root object could be found or not.
     */    
    public boolean omitRootObject(String workspaceUUID, String newRootUUID) {
        
        String rootUUID = "";
        
        for (int i = 0; i < persistCalls.persistedSPOsToAdd.size(); i++) {
            if (persistCalls.persistedSPOsToAdd.get(i).getParentUUID().equals(workspaceUUID)) {
                rootUUID = persistCalls.persistedSPOsToAdd.get(i).getUUID();
                persistCalls.persistedSPOsToAdd.remove(i);
                break;
            }  
        }
        
        if (rootUUID.equals("")) {
            return false;
        }
        
        for (int i = 0; i < persistCalls.propertyDiffPersists.size(); i++) {
            if (persistCalls.propertyDiffPersists.get(i).getUUID().equals(rootUUID)) {
                persistCalls.propertyDiffPersists.remove(i);
                i--;
            }
        }
        
        if (!newRootUUID.equals("")) {
            for (int i = 0; i < persistCalls.persistedSPOsToAdd.size(); i++) {
                PersistedSPObject child = persistCalls.persistedSPOsToAdd.get(i);
                if (child.getParentUUID().equals(rootUUID)) {                    
                    persistCalls.persistedSPOsToAdd.add(new PersistedSPObject(
                            newRootUUID,                            
                            child.getType(),
                            child.getUUID(),
                            child.getIndex()));
                    persistCalls.persistedSPOsToAdd.remove(i);
                    i--;
                }
            }                        
        }
        
        return true;
        
    }

    public HashMap<String, PersistedSPObject> getOldObjectMap() {
        return oldObjectMap;
    }

    public HashMap<String, PersistedSPObject> getNewObjectMap() {
        return newObjectMap;
    }

    /**
     * Returns the name of the type of the object with the given id in this Differ's old workspace.
     * @param uuid
     * @return A string representing the type of the object with the given uuid, or null if it cannot be found.
     * @throws SQLObjectException 
     */
    
    public String getObjectType(String uuid) throws SQLObjectException {
        if (oldObjectMap.get(uuid) != null) {
            return oldObjectMap.get(uuid).getSimpleType();
        } else if (newObjectMap.get(uuid) != null) {
            return newObjectMap.get(uuid).getSimpleType();
        } else {
            throw new SQLObjectException("Could not find object in map");
        }
    }
    
    public String getOldObjectName(String uuid) {
        return (String) getOldPropertyValue(uuid, "name");
    }
    
    public String getNewObjectName(String uuid) {
        return (String) getNewPropertyValue(uuid, "name");
    }
    
    /**
     * Method to have the Differ take its persist calls and convert them to a list of ordered DiffChunks
     * that can be used by CompareDMFormatter's generateEnglishDescriptions method.
     * 
     * @param sourceRoot The UUID of the object whose children you want DiffChunks for (excluding the object itself)
     * @throws SQLObjectException 
     */
    public List<DiffChunk<DiffInfo>> getDiffChunks(String rootUUID) throws SQLObjectException {
        
        // A map containing DiffChunk objects for every object relevant to the comparison.
        // This means all added/removed/changed objects, and all their ancestors.
        Map<String, DiffChunk<DiffInfo>> diffChunks = new HashMap<String, DiffChunk<DiffInfo>>();
        
        // A map of containing for looking up the parent UUIDs of objects that have been added/removed/changed.
        Map<String, String> parentMap = new HashMap<String, String>();
        
        // Add the removed objects to the DiffChunk map and map their ancestors.
        for (PersistedSPObject o : persistCalls.persistedSPOsToRemove) {
            String uuid = o.getUUID();
            getObjectType(uuid);
            getOldObjectName(uuid);           
            DiffInfo d = new DiffInfo(getObjectType(uuid), getOldObjectName(uuid));
            diffChunks.put(uuid, new DiffChunk<DiffInfo>(d, DiffType.LEFTONLY));                     
            addAncestorsToMap(uuid, rootUUID, oldObjectMap, parentMap);
        }

        // Add the added objects to the DiffChunk map and map their ancestors.
        for (PersistedSPObject o : persistCalls.persistedSPOsToAdd) {            
            String uuid = o.getUUID();
            DiffInfo d = new DiffInfo(getObjectType(uuid), getNewObjectName(uuid));
            diffChunks.put(uuid, new DiffChunk<DiffInfo>(d, DiffType.RIGHTONLY));
            addAncestorsToMap(uuid, rootUUID, newObjectMap, parentMap);
        }
        
        // Add property changes to the DiffChunks if they are already there.
        // If not, add new ones to the DiffChunk map and map the ancestors.
        for (PersistedSPOProperty p : persistCalls.propertyDiffPersists) {                        
            String uuid = p.getUUID();
            if (!diffChunks.containsKey(uuid)) {
                DiffInfo d = new DiffInfo(getObjectType(uuid), getOldObjectName(uuid));
                diffChunks.put(uuid, new DiffChunk<DiffInfo>(d, DiffType.MODIFIED));
                addAncestorsToMap(uuid, rootUUID, oldObjectMap, parentMap);                
            }
            
            if (diffChunks.get(uuid).getType() == DiffType.MODIFIED) {
                try {
                    Set<String> interestingProperties = PersisterUtils.getInterestingPropertyNames(
                        oldObjectMap.get(uuid).getType());
                    if (interestingProperties.contains(p.getPropertyName())) {
                        String oldValue = String.valueOf(p.getOldValue());
                        String newValue = String.valueOf(p.getNewValue());
                        if (p.getDataType() == DataType.STRING) {
                            if (p.getOldValue() != null) oldValue = "\"" + oldValue + "\"";
                            if (p.getNewValue() != null) newValue = "\"" + newValue + "\"";
                        }
                        PropertyChange change = new PropertyChange(p.getPropertyName(), 
                                oldValue, newValue);
                        diffChunks.get(p.getUUID()).addPropertyChange(change);
                        logger.debug("Added change: " + change);
                    }
                } catch (Exception e) {
                    throw new SQLObjectException("Error looking up interesting property names", e);
                }            
            }
        }
        
        diffChunks.remove(rootUUID);
        parentMap.remove(rootUUID);        
        
        // Add unchanged objects that are ancestors of those changed by searching up from the changed objects.
        Iterator<String> i = parentMap.keySet().iterator();
        while (i.hasNext()) {            
            String leafUUID = i.next();
            
            String nextUUID = parentMap.get(leafUUID);
            logger.debug("Leaf " + getObjectType(leafUUID) + ": " + leafUUID);
            // Loop while the UUID of the object to add is not the root object, and while it has not already been added.
            while (!nextUUID.equals(rootUUID) && !diffChunks.containsKey(nextUUID)) {
                logger.debug(nextUUID);
                DiffInfo d = new DiffInfo(getObjectType(nextUUID), getOldObjectName(nextUUID));
                diffChunks.put(nextUUID, new DiffChunk<DiffInfo>(d, DiffType.SAME));
                parentMap.put(nextUUID, oldObjectMap.get(nextUUID).getParentUUID());
                nextUUID = parentMap.get(nextUUID);
            }
            
        }
        
        DiffChunkTreeNode root = new DiffChunkTreeNode(rootUUID, null);
        root.constructTree(diffChunks, parentMap);
        return root.buildOrderedList();
    }

    private void addAncestorsToMap(String uuid, String rootUUID, 
            Map<String, PersistedSPObject> objectMap, 
            Map<String, String> parentMap) {
        String nextUUID = uuid;
        while (!nextUUID.equals(rootUUID) && !parentMap.containsKey(nextUUID)) {
            parentMap.put(nextUUID, objectMap.get(nextUUID).getParentUUID());
            nextUUID = objectMap.get(nextUUID).getParentUUID();
        }
    } 
}