/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2005 by Kappich+Kni� Systemberatung Aachen (K2S)
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
package de.bsvrz.puk.config.xmlFile.properties;

import de.bsvrz.dav.daf.main.config.SystemObjectInfo;

/**
 * Diese Klasse stellt eine AttributlistenDefinition dar, diese wird in der K2S.DTD definiert.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5467 $
 */
public class AttributeListProperties extends ConfigurationObjectProperties {

	/**
	 * Speichert alle Attribute und Attributlisten
	 */
	AttributeProperties _attributeAndAttributeList[] = new AttributeProperties[0];

	public AttributeListProperties(String name, String pid, long id, String typePid, SystemObjectInfo info) {
		super(name, pid, id, typePid, info);
	}

	/**
	 * Das Array speichert alle Objekte, die vom Typ PlainAttributeProperties oder vom Typ ListAttributeProperties sind.
	 * An Position [0] ist das Objekt gespeichert, das auch als erstes eingelesen wurde.
	 * @return Objekte vom Typ PlainAttributeProperties und ListAttributeProperties. Sind keine Objekte vorhanden, so ist das Array leer
	 */
	public AttributeProperties[] getAttributeAndAttributeList() {
		return _attributeAndAttributeList;
	}

	/**
	 *
	 * @param attributeAndAttributeList Array, das Objekte vom Typ PlainAttributeProperties oder vom Typ ListAttributeProperties enth�lt. Die Position der Elemente bleibt auch beim speichern erhalten.
	 */
	public void setAttributeAndAttributeList(AttributeProperties[] attributeAndAttributeList) {
		_attributeAndAttributeList = attributeAndAttributeList;
	}
}
