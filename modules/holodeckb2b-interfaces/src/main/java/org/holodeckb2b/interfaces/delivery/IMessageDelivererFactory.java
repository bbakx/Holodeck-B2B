/**
 * Copyright (C) 2014 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.interfaces.delivery;

import java.util.Map;

import org.holodeckb2b.interfaces.pmode.IPMode;

/**
 * @deprecated Replaced by {@link IDeliveryMethod}. This interface will be removed in the next version!
 */
@Deprecated
public interface IMessageDelivererFactory {

    void init(Map<String, ?> settings) throws MessageDeliveryException;

    IMessageDeliverer createMessageDeliverer() throws MessageDeliveryException;
}
