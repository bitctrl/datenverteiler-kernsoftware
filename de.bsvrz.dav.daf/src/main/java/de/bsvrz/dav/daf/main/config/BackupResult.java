/*
 * Copyright 2010 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.dav.daf.
 * 
 * de.bsvrz.dav.daf is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.dav.daf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with de.bsvrz.dav.daf; If not, see <http://www.gnu.org/licenses/>.

 * Contact Information:
 * Kappich Systemberatung
 * Martin-Luther-Straße 14
 * 52062 Aachen, Germany
 * phone: +49 241 4090 436 
 * mail: <info@kappich.de>
 */

package de.bsvrz.dav.daf.main.config;

/** 
 * Interface, das die Rückgabe eines Konfigurationsbackups speichert
 * @author Kappich Systemberatung
 * @version $Revision$ 
 * */
public interface BackupResult {

	/**
	 * Korrekt gesicherte Dateien
	 *
	 * @return Anzahl der gesicherten Dateien
	 */
	long getCompleted();

	/**
	 * Nicht gesicherte Dateien
	 *
	 * @return Anzahl der nicht gesicherten dateien (z.B. wegen Lesefehlern, zu wenig Speicherplatz etc.)
	 */
	long getFailed();

	/**
	 * Anzahl der Dateien, die gesichert werden sollten.
	 *
	 * @return getCompleted() + getFailed()
	 */
	long getTotal();

	/**
	 * Pfad in dem die Sicherung angelegt wurde. Befindet sich auf dem System, auf dem die Konfiguration läuft.
	 *
	 * @return Absoluter Pfad als String
	 */
	String getPath();
}
