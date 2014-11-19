/*******************************************************************************
 * Crafter Studio Web-content authoring solution
 *     Copyright (C) 2007-2013 Crafter Software Corporation.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.craftercms.cstudio.alfresco.service.exception;

public class InvalidTypeException extends ServiceException {

	/**
	 * 
	 */
	protected static final long serialVersionUID = 809389373419655873L;

	public InvalidTypeException() {}
	
	public InvalidTypeException(Exception e) {
		super(e);
	}
	
	public InvalidTypeException(String message) {
		super(message);
	}
	
	public InvalidTypeException(String message, Exception e) {
		super(message, e);
	}

}
