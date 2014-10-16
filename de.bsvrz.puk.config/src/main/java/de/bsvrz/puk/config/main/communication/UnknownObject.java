/*
 * Copyright 2011 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.puk.config.
 * 
 * de.bsvrz.puk.config is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.puk.config is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.puk.config; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.bsvrz.puk.config.main.communication;

import de.bsvrz.dav.daf.main.Data;
import de.bsvrz.dav.daf.main.config.*;
import de.bsvrz.puk.config.configFile.datamodel.AbstractConfigSystemObject;

import java.util.Collection;

/**
 * Diese Klasse imitiert ein Systemobjekt und wird bei der Kommunikation mit Anwendungen benutzt, die der lokalen Konfiguration nicht bekannt sind.
 *
 * @author Kappich Systemberatung
 * @version : 0000 $
 */
public class UnknownObject extends AbstractConfigSystemObject{

	private final long _id;

	/**
	 * Erstellt ein Dummy-Systemobjekt
	 * @param id Id
	 * @param configurationArea Konfigurationbereich (irgendeiner, wird nicht gebraucht)
	 */
	public UnknownObject(final long id, final ConfigurationArea configurationArea) {
		super(configurationArea);
		_id = id;
	}

	public long getId() {
		return _id;
	}

	public SystemObjectType getType() {
		return getConfigurationArea().getDataModel().getType("typ.applikation");
	}

	public String getPid() {
		return "";
	}

	public String getName() {
		return String.valueOf("Unbekannt{" + _id + "}");
	}

	public void setName(final String name) throws ConfigurationChangeException {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public boolean isValid() {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public void invalidate() throws ConfigurationChangeException {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public Data getConfigurationData(final AttributeGroup atg, final Aspect asp) {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public Data getConfigurationData(final AttributeGroupUsage atgUsage) {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public void setConfigurationData(final AttributeGroup atg, final Aspect asp, final Data data) throws ConfigurationChangeException {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public void setConfigurationData(final AttributeGroupUsage atgUsage, final Data data) throws ConfigurationChangeException {
		throw new UnsupportedOperationException("Nicht implementiert");
	}

	public Collection<AttributeGroupUsage> getUsedAttributeGroupUsages() {
		throw new UnsupportedOperationException("Nicht implementiert");
	}
}
