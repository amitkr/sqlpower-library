/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.query;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.UUID;

import org.apache.log4j.Logger;


public abstract class AbstractItem implements Item {
    
    private static final Logger logger = Logger.getLogger(AbstractItem.class);

	/**
	 * The width that this item should take up when displayed in a column of a table.
	 */
	private Integer columnWidth;
	
	private final UUID uuid;
	
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	
	private Container parent;
	
	private String name;
	
	public AbstractItem() {
	    uuid = UUID.randomUUID();
	}
	
	public AbstractItem(String uuid) {
	    if (uuid == null) {
	        this.uuid = UUID.randomUUID();
	    } else {
	        this.uuid = UUID.fromString(uuid);
	    }
	}
	
	public void setColumnWidth(Integer width) {
		this.columnWidth = width;
	}
	
	public Integer getColumnWidth() {
		return columnWidth;
	}
	
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
    
    protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, int oldValue, int newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (logger.isDebugEnabled()) {
            logger.debug("Firing property change \"" + propertyName + "\" to " +
                    pcs.getPropertyChangeListeners().length +
                    " listeners: " + Arrays.toString(pcs.getPropertyChangeListeners()));
        }
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }
    
    public Container getParent() {
        return parent;
    }
    
    public void setParent(Container parent) {
        this.parent = parent;
    }
    
    /**
     * Returns the short name for this object.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the name for this object 
     */
    public void setName(String name) {
        this.name = name;
    }
    
    public UUID getUUID() {
        return uuid;
    }

}
