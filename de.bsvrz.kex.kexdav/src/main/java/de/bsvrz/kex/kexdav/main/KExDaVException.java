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

package de.bsvrz.kex.kexdav.main;

/**
 * Exception, die KExDaV bei falscher Parametrierung oder fehlenden Objekten auslöst
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public abstract class KExDaVException extends Exception{

	/**
	 * Konstruktor
	 * @param message Fehlermeldung
	 */
	protected KExDaVException(final String message) {
		super(message);
	}
	/**
	 *
	 * Konstruktor
	 * @param cause Fehler
	 */
	public KExDaVException(final Throwable cause) {
		// Anstatt super(cause) aufzurufen, wird hier die KExDaVException manuell erstellt.
		// Das hat den Vorteil das vor Warnmeldungen u.ä. nicht noch sinnloserweise
		// "java.lang.IllegalArgumentException:" o.ä. steht. Der Stacktrace und damit die ursächliche Exception bleibt aber erhalten.
        super(cause.getMessage());
		fillInStackTrace();
		initCause(cause);
	}
}
