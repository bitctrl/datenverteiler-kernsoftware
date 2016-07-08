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

package de.bsvrz.kex.kexdav.parameterloader;

import de.bsvrz.dav.dav.util.accessControl.ObjectCollection;

import java.util.Collections;
import java.util.List;

/**
 * Datenaustauschparameter
 *
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class ExchangeDataParameter{

	private final ObjectCollection _objectCollection;

	private final List<DataExchangeIdentification> _identificationList;

	/**
	 * Erstelle neue Datenaustauschparameter
	 * @param objectCollection Objektauswahl
	 * @param identificationList Datenaustauschspezifikationen
	 */
	public ExchangeDataParameter(final ObjectCollection objectCollection, final List<DataExchangeIdentification> identificationList) {

		_objectCollection = objectCollection;
		_identificationList = identificationList;
	}

	/**
	 * Gibt die Auswahl der Objekte zurück
	 * @return die Auswahl der Objekte
	 */
	public ObjectCollection getObjectRegionSet() {
		return _objectCollection;
	}

	/**
	 * Gibt die Datenidentifikationen zurück
	 * @return die Datenidentifikationen
	 */
	public List<DataExchangeIdentification> getIdentificationList() {
		return Collections.unmodifiableList(_identificationList);
	}

	@Override
	public boolean equals(final Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		final ExchangeDataParameter other = (ExchangeDataParameter)o;

		if(!_identificationList.equals(other._identificationList)) return false;
		if(!_objectCollection.equals(other._objectCollection)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = _objectCollection.hashCode();
		result = 31 * result + _identificationList.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "ExchangeDataParameter{" + "_objectRegionSet=" + _objectCollection + ", _identificationList=" + _identificationList + '}';
	}
}
