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

package de.kappich.pat.gnd.needlePlugin;


import de.kappich.pat.gnd.pluginInterfaces.DOTDefinitionDialog;
import de.kappich.pat.gnd.pluginInterfaces.DisplayObjectType;

import javax.swing.*;

/**
 * Dialog für die Einstellungen zu Notiz-Nadel-DOT. Leer da er nicht augerufen werden kann.
 * 
 * @author Kappich Systemberatung
 * @version $Revision$
 *
 */
public class DOTNeedleDialog implements DOTDefinitionDialog {
	

	public JPanel getAdditionalCharacteristicsPanel( final DisplayObjectType displayObjectType) {
	    return null;
    }

	public JPanel getDOTItemDefinitionPanel() {
		return null;
	}

	public void saveDisplayObjectType() {
	}
}
