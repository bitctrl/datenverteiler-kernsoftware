/*
 * Copyright 2009 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.kappich.pat.gnd.
 * 
 * de.kappich.pat.gnd is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * de.kappich.pat.gnd is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.kappich.pat.gnd.  If not, see <http://www.gnu.org/licenses/>.

 * Contact Information:
 * Kappich Systemberatung
 * Martin-Luther-Straße 14
 * 52062 Aachen, Germany
 * phone: +49 241 4090 436 
 * mail: <info@kappich.de>
 */
package de.kappich.pat.gnd.pluginInterfaces;


import javax.swing.JPanel;

/**
 * Ein Interface für die plugin-spezifischen Panels der Darstellungstypdefinition
 * und das Speichern des Darstellungstypen.
 * 
 * @author Kappich Systemberatung
 * @version $Revision$
 *
 */
public interface DOTDefinitionDialog {
	
	/**
	 * Liefert ein Panel zurück, in dem zusätzliche Charakteristika angezeigt werden.
	 * Wird aktuell nur von DOTPoint zur Anzeige der Grundfigur-Definitionen benutzt.
	 * 
	 * @param displayObjectType ein Darstellungstyp
	 * @return das Panel oder <code>null</code>
	 */
	public JPanel getAdditionalCharacteristicsPanel( final DisplayObjectType displayObjectType);
	
	/**
	 * Liefert das zentrale Panel zur Darstellungstypdefinition des Plugins.
	 */
	public JPanel getDOTItemDefinitionPanel();
	
	/**
	 * Speichert den neuen oder veränderten Darstellungstypen ab.
	 */
	public void saveDisplayObjectType();
	
}
