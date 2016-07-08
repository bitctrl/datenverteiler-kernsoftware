/*
 * Copyright 2011 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.kex.kexdav.
 * 
 * de.bsvrz.kex.kexdav is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.kex.kexdav is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.kex.kexdav.  If not, see <http://www.gnu.org/licenses/>.

 * Contact Information:
 * Kappich Systemberatung
 * Martin-Luther-Straße 14
 * 52062 Aachen, Germany
 * phone: +49 241 4090 436 
 * mail: <info@kappich.de>
 */

package de.bsvrz.kex.kexdav.management;

import de.bsvrz.dav.daf.main.config.SystemObject;
import de.bsvrz.kex.kexdav.main.KExDaV;

/**
 * KExDaV-Verwaltung von Nachrichten. Vorgesehen sind auch die Abfrage von Statistiken usw.
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class KExDaVManager extends AbstractObservable implements ManagerInterface {

	private final KExDaV _kExDaV;

	/**
	 * Konstruktor
	 * @param kExDaV Hauptklasse KExDaV
	 */
	public KExDaVManager(final KExDaV kExDaV) {
		_kExDaV = kExDaV;
	}

	public SystemObject getKExDaVObject() {
		return _kExDaV.getKExDaVObject();
	}

	@Override
	public String toString() {
		return "KExDaVManager{" + "_kExDaV=" + _kExDaV + '}';
	}
}
