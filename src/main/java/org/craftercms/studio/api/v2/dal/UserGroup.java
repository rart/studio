/*
 * Copyright (C) 2007-2018 Crafter Software Corporation. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.craftercms.studio.api.v2.dal;

import java.io.Serializable;
import java.time.ZonedDateTime;

public class UserGroup implements Serializable {

    private static final long serialVersionUID = -1030917735504714929L;

    private GroupDAO group;
    private ZonedDateTime recordLastUpdated;

    public GroupDAO getGroup() {
        return group;
    }

    public void setGroup(GroupDAO group) {
        this.group = group;
    }

    public ZonedDateTime getRecordLastUpdated() {
        return recordLastUpdated;
    }

    public void setRecordLastUpdated(ZonedDateTime recordLastUpdated) {
        this.recordLastUpdated = recordLastUpdated;
    }
}