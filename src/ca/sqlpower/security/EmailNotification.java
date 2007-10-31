/*
 * Copyright (c) 2007, SQL Power Group Inc.
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of SQL Power Group Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package ca.sqlpower.security;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ca.sqlpower.sql.DatabaseObject;
import ca.sqlpower.sql.SQL;
import ca.sqlpower.util.UnknownFreqCodeException;

/**
 * The EmailNotification class answers questions about email
 * notification for users or groups on specific objects, and allows
 * updating of notification preferences.
 * 
 * @author Gillian Mereweather
 * @author Jonathan Fuerth
 * @version $Id$
 */
public class EmailNotification implements java.io.Serializable {

	/**
	 * The name of the column indicating whether to send emails at red status
	 * on either pl_user_notification or pl_group_notification.
	 */
	private static final String RED_STATUS = "EMAIL_RED_IND";
	
	/**
	 * The name of the column indicating whether to send emails at yellow status
	 * on either pl_user_notification or pl_group_notification.
	 */
	private static final String YELLOW_STATUS = "EMAIL_YELLOW_IND";
	
	/**
	 * The name of the column indicating whether to send emails at green status
	 * on either pl_user_notification or pl_group_notification.
	 */
	private static final String GREEN_STATUS = "EMAIL_GREEN_IND";

	/**
	 * Stores a pl_user_notification record in the database.  
	 * This could update an existing record or insert a new one.
	 *
	 * <p>Security requirements: setter requires modify permission on notifyUser.
	 *
	 * @param sm The current logged-in user's security manager.
	 */
	public static void setPref(Connection con,
			PLSecurityManager sm,
			PLUser notifyUser,
			DatabaseObject notifyAbout,
			String viewKpi,
			String emailRed,
			String emailYellow,
			String emailGreen)
	throws SQLException, PLSecurityException {

		sm.checkModify(con, notifyUser);
		setPref(con, notifyUser.getUserId(), true, notifyAbout, 
				viewKpi, emailRed, emailYellow, emailGreen);
	}

	/**
	 * Stores a pl_group_notification record in the database.  
	 * This may update an existing record or insert a new one.
	 *
	 * <p>Security requirements: setter requires modify permission on notifyGroup.
	 *
	 * @param sm The current logged-in user's security manager.
	 */
	public static void setPref(Connection con,
			PLSecurityManager sm,
			PLGroup notifyGroup,
			DatabaseObject notifyAbout,
			String viewKpi,
			String emailRed,
			String emailYellow,
			String emailGreen)
	throws SQLException, PLSecurityException {

		sm.checkModify(con, notifyGroup);
		setPref(con, notifyGroup.getGroupName(), false, notifyAbout, 
				viewKpi, emailRed, emailYellow, emailGreen);
	}

	/**
	 * Used by the other setPref methods.  Doesn't do a security
	 * check, so you can't call it from outside.
	 *
	 * @param nameIsUser True if this is for user notifications; false
	 * if this is for group notifications.
	 */
	protected static void setPref(Connection con,
			String notifyName,
			boolean nameIsUser,
			DatabaseObject notifyAbout,
			String viewKpi,
			String emailRed,
			String emailYellow,
			String emailGreen)
	throws SQLException {

		Statement stmt = null;
		ResultSet rs = null;
		boolean bFirst=true;
		try {
			stmt = con.createStatement();
			StringBuffer sql=new StringBuffer();

			sql.setLength(0);
			if (nameIsUser) {
				sql.append("SELECT count(*) FROM pl_user_notification");
				sql.append(" WHERE user_id=");
			} else {
				sql.append("SELECT count(*) FROM pl_group_notification");
				sql.append(" WHERE group_name=");
			}
			sql.append(SQL.quote(notifyName));
			sql.append(" AND object_type=").append(SQL.quote(notifyAbout.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(notifyAbout.getObjectName()));

			rs = stmt.executeQuery(sql.toString());

			sql.setLength(0);

			// If there is already a row, update the flags
			if (rs.next()) {
				int rowCount = rs.getInt(1);

				/* If the record exists */
				if (rowCount > 0) {
					if (nameIsUser) {
						sql.append("UPDATE pl_user_notification");
					} else {
						sql.append("UPDATE pl_group_notification");
					}
					sql.append(" SET");

					if(!viewKpi.equals("")){
						sql.append(" view_kpi_ind=").append(SQL.quote(viewKpi));
						bFirst=false;
					}
					if(!emailRed.equals("")){
						if(!bFirst){
							sql.append(",");
						}
						sql.append(" email_red_ind=").append(SQL.quote(emailRed));
						bFirst=false;
					}
					if(!emailYellow.equals("")){
						if(!bFirst){
							sql.append(",");
						}
						sql.append(" email_yellow_ind=").append(SQL.quote(emailYellow));
						bFirst=false;
					}
					if(!emailGreen.equals("")){
						if(!bFirst){
							sql.append(",");
						}
						sql.append(" email_green_ind=").append(SQL.quote(emailGreen));
						bFirst=false;
					}

					if (nameIsUser) {
						sql.append(" WHERE user_id=");
					} else {
						sql.append(" WHERE group_name=");
					}
					sql.append(SQL.quote(notifyName));
					sql.append(" AND object_type=").append(SQL.quote(notifyAbout.getObjectType()));
					sql.append(" AND object_name=").append(SQL.quote(notifyAbout.getObjectName()));

					// The row does not exist - insert a record
				} else {
					if (nameIsUser) {
						sql.append("INSERT INTO pl_user_notification(user_id,");
					} else {
						sql.append("INSERT INTO pl_group_notification(group_name,");
					}
					sql.append(" object_type, object_name, view_kpi_ind,");
					sql.append(" email_red_ind, email_yellow_ind, email_green_ind)");
					sql.append(" VALUES( ");
					sql.append(SQL.quote(notifyName)).append(",");
					sql.append(SQL.quote(notifyAbout.getObjectType())).append(",");
					sql.append(SQL.quote(notifyAbout.getObjectName())).append(",");
					sql.append(SQL.quote(viewKpi)).append(",");
					sql.append(SQL.quote(emailRed)).append(",");
					sql.append(SQL.quote(emailYellow)).append(",");
					sql.append(SQL.quote(emailGreen)).append(")");
				} // end if (the record exists)
			} // end if (the rs has a value)

			stmt.executeUpdate(sql.toString());
		} finally {
			if (rs != null) {
				rs.close();
			}
			if(stmt != null) {
				stmt.close();
			}
		}
	}

	/**
	 * Returns the email notification preference of green status for
	 * the given user on the given object.  Does nothing about groups
	 * that this user may belong to.
	 */
	public static boolean checkUserGreenStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, user.getUserId(), true, dbObj, GREEN_STATUS);
	}

	/**
	 * Returns the email notification preference of yellow status for
	 * the given user on the given object.  Does nothing about groups
	 * that this user may belong to.
	 */
	public static boolean checkUserYellowStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, user.getUserId(), true, dbObj, YELLOW_STATUS);
	}

	/**
	 * Returns the email notification preference of red status for
	 * the given user on the given object.  Does nothing about groups
	 * that this user may belong to.
	 */
	public static boolean checkUserRedStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, user.getUserId(), true, dbObj, RED_STATUS);
	}

	/**
	 * Returns the email notification preferences for the 
	 * given user on the given object, including the
	 * additional checks for the groups the user belongs to.
	 */
	private static boolean checkUserAndGroupStatus(Connection con, 
			PLUser user, DatabaseObject dbObj, String statusType) throws SQLException {
		if (checkStatus(con, user.getUserId(), true, dbObj, statusType)) {
			return true;
		}
		Iterator groups = user.getGroups(con).iterator();
		while (groups.hasNext()) {
			PLGroup g = (PLGroup) groups.next();
			if (checkStatus(con, g.getGroupName(), false, dbObj, statusType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the email notification preferences of green status 
	 * for the given user on the given object, including the
	 * additional checks for the groups the user belongs to.
	 */
	public static boolean checkUserAndGroupGreenStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkUserAndGroupStatus(con, user, dbObj, GREEN_STATUS);
	}

	/**
	 * Returns the email notification preferences of yellow status 
	 * for the given user on the given object, including the
	 * additional checks for the groups the user belongs to.
	 */
	public static boolean checkUserAndGroupYellowStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkUserAndGroupStatus(con, user, dbObj, YELLOW_STATUS);
	}

	/**
	 * Returns the email notification preferences of red status 
	 * for the given user on the given object, including the
	 * additional checks for the groups the user belongs to.
	 */
	public static boolean checkUserAndGroupRedStatus(Connection con, 
			PLUser user, DatabaseObject dbObj) throws SQLException {
		return checkUserAndGroupStatus(con, user, dbObj, RED_STATUS);
	}


	/**
	 * Returns the email notification preferences of green status 
	 * for the given group on the given object.
	 */
	public static boolean checkGroupGreenStatus(Connection con, 
			PLGroup group, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, group.getGroupName(), false, dbObj, GREEN_STATUS);
	}

	/**
	 * Returns the email notification preferences of green status 
	 * for the given group on the given object.
	 */
	public static boolean checkGroupYellowStatus(Connection con, 
			PLGroup group, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, group.getGroupName(), false, dbObj, YELLOW_STATUS);
	}

	/**
	 * Returns the email notification preferences of green status 
	 * for the given group on the given object.
	 */
	public static boolean checkGroupRedStatus(Connection con, 
			PLGroup group, DatabaseObject dbObj) throws SQLException {
		return checkStatus(con, group.getGroupName(), false, dbObj, RED_STATUS);
	}

	/**
	 * Used internally by the user and group versions of checkStatus.
	 */
	private static boolean checkStatus(Connection con,
			String name,
			boolean nameIsUser,
			DatabaseObject dbObj,
			String statusType) throws SQLException {
		Statement stmt = null;
		try {
			StringBuffer sql = new StringBuffer();

			sql.append("SELECT " + statusType);

			if(nameIsUser){
				sql.append(" FROM pl_user_notification");
				sql.append(" WHERE user_id=").append(SQL.quote(name));
			} else {
				sql.append(" FROM pl_group_notification");
				sql.append(" WHERE group_name=").append(SQL.quote(name));
			}
			sql.append(" AND object_type=").append(SQL.quote(dbObj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(dbObj.getObjectName()));

			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(sql.toString());
			while(rs.next()) {
				String prefStatus = rs.getString(1);
				if (prefStatus != null && prefStatus.equals("Y")) {
					return true;
				}
			}

			// There wasn't a matching entry in the table
			return false;

		} finally {
			if(stmt != null) {
				stmt.close();
			}
		}
	}
	
	/**
	 * Internal method that returns a list of EmailRecipients containing
	 * the email and name of users who are indicated for receiving emails
	 * of given status of given DatabaseObject. This currently does not
	 * check for groups that users could belong to. 
	 */
	private static List<EmailRecipient> findEmailRecipients(Connection con, DatabaseObject dbObj,
			String statusType) throws SQLException {
		List<EmailRecipient> emailRecipients = new ArrayList<EmailRecipient>();
		
        Statement stmt = null;
        try {
			StringBuffer sql = new StringBuffer();
			sql.append("SELECT user_name, email_address FROM pl_user in (");
			sql.append("SELECT user_id");
			sql.append(" FROM pl_user_notification");
			
			sql.append(" WHERE " + statusType + " = " + SQL.quote("Y"));
			sql.append(" AND object_type = " + SQL.quote(dbObj.getObjectType()));
			sql.append(" AND object_name = " + SQL.quote(dbObj.getObjectName()) + ")");

            stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(sql.toString());
			
			while(rs.next()) {
				EmailRecipient er = new EmailRecipient(rs.getString(1), rs.getString(2));
				emailRecipients.add(er);
			};

        } finally {
            if(stmt != null) {
                stmt.close();
            }
        }
		
		return emailRecipients;
	}

	/**
	 * Returns a list of EmailRecipients to whom emails of green status
	 * of given DatabaseObject should be sent to. This does not check for
	 * groups that users could belong to.
	 */
	public static List<EmailRecipient> findGreenEmailRecipients(Connection con, DatabaseObject dbObj) 
			throws UnknownFreqCodeException, PLSecurityException, SQLException {
		return findEmailRecipients(con, dbObj, GREEN_STATUS) ;
	}
	
	/**
	 * Returns a list of EmailRecipients to whom emails of yellow status
	 * of given DatabaseObject should be sent to. This does not check for
	 * groups that users could belong to.
	 */
	public static List<EmailRecipient> findYellowEmailRecipients(Connection con, DatabaseObject dbObj) 
			throws UnknownFreqCodeException, PLSecurityException, SQLException {
		return findEmailRecipients(con, dbObj, YELLOW_STATUS) ;
	}

	/**
	 * Returns a list of EmailRecipients to whom emails of red status
	 * of given DatabaseObject should be sent to. This does not check for
	 * groups that users could belong to.
	 */
	public static List<EmailRecipient> findRedEmailRecipients(Connection con, DatabaseObject dbObj) 
			throws UnknownFreqCodeException, PLSecurityException, SQLException {
		return findEmailRecipients(con, dbObj, RED_STATUS) ;
	}

	/**
	 * This method is normally called from {@link
	 * PLSecurityManager#deleteDatabaseObject}, but you can call it
	 * directly if you want.  It removes everything email
	 * notification-ish about a database object.  It will not
	 * magically remove the object's own data, but it will zap all the
	 * necessary rows from the following:
	 * 
	 * <ul>
	 *  <li>PL_USER_NOTIFICATION_LOG
	 *  <li>PL_USER_NOTIFICATION
	 * </ul>
	 *
	 * <p>It is expected that the given connection will <b>not</b> be
	 * in autocommit mode.
	 *
	 * <p>SECURITY REQUIREMENT: sm must allow DELETE permission on obj.
	 *
	 * @param con An open connection to the database in question.
	 * Should not be in autocommit mode, but this is not enforced.
	 * @param sm A security manager that allows deletion of the object in question.
	 * @param obj The database object we're evicting.
	 */
	public static void deleteDatabaseObject(Connection con, PLSecurityManager sm,
			DatabaseObject obj)
	throws PLSecurityException, SQLException {

		sm.checkDelete(con, obj);

		Statement stmt = null;
		try {
			stmt = con.createStatement();

			StringBuffer sql = new StringBuffer();
			sql.append("DELETE FROM pl_user_notification WHERE object_type=");
			sql.append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

			sql.setLength(0);
			sql.append("DELETE FROM pl_user_notification_log WHERE object_type=");
			sql.append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

			sql.setLength(0);
			sql.append("DELETE FROM pl_group_notification");
			sql.append(" WHERE object_type=").append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

		} finally {
			if (stmt != null) {
				stmt.close();
			}
		}
	}

	/**
	 * This method is normally called from {@link
	 * PLSecurityManager#renameDatabaseObject}, but you can call it
	 * directly if you want.  It renames (points to a new object name)
	 * everything email notification-ish about a database object.  It
	 * will not magically move the object's own data, but it will
	 * repoint all the necessary rows from the following:
	 * 
	 * <ul>
	 *  <li>PL_USER_NOTIFICATION_LOG
	 *  <li>PL_USER_NOTIFICATION
	 *  <li>PL_GROUP_NOTIFICATION
	 * </ul>
	 *
	 * <p>It is expected that the given connection will <b>not</b> be
	 * in autocommit mode.
	 *
	 * <p>SECURITY REQUIREMENT: sm must allow MODIFY permission on obj.
	 *
	 * @param con An open connection to the database in question.
	 * Should not be in autocommit mode, but this is not enforced.
	 * @param sm A security manager that allows deletion of the object in question.
	 * @param obj The database object we're in the process of
	 * renaming.  obj.getObjectName() must return the old object name!
	 * @param newName the new name we are assigning to the object.
	 */
	public static void renameDatabaseObject(Connection con, PLSecurityManager sm,
			DatabaseObject obj, String newName)
	throws PLSecurityException, SQLException {

		sm.checkModify(con, obj);

		Statement stmt = null;
		try {
			stmt = con.createStatement();

			StringBuffer sql = new StringBuffer();
			sql.append("UPDATE pl_user_notification SET object_name=").append(SQL.quote(newName));
			sql.append(" WHERE object_type=").append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

			sql.setLength(0);
			sql.append("UPDATE pl_user_notification_log SET object_name=").append(SQL.quote(newName));
			sql.append(" WHERE object_type=").append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

			sql.setLength(0);
			sql.append("UPDATE pl_group_notification SET object_name=").append(SQL.quote(newName));
			sql.append(" WHERE object_type=").append(SQL.quote(obj.getObjectType()));
			sql.append(" AND object_name=").append(SQL.quote(obj.getObjectName()));
			stmt.executeUpdate(sql.toString());

		} finally {
			if (stmt != null) {
				stmt.close();
			}
		}
	}
	
	
	/**
	 * A simple inner class to hold the name and email of an
	 * user that has been indicated as an email recipient
	 */
	public static class EmailRecipient {
		
		private String name;
		private String email;
		
		public EmailRecipient(String name, String email) {
			this.name = name;
			this.email = email;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getEmail() {
			return email;
		}
		
		public void setEmail(String email) {
			this.email = email;
		}
		
	}
}

