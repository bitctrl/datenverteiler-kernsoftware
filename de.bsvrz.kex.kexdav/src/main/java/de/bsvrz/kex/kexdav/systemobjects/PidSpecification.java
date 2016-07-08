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

package de.bsvrz.kex.kexdav.systemobjects;

import de.bsvrz.dav.daf.main.config.DataModel;
import de.bsvrz.dav.daf.main.config.SystemObject;

/**
 * @author Kappich Systemberatung
 * @version $Revision$
 */
public class PidSpecification extends ObjectSpecification {

	private final String _pid;

	public PidSpecification(final String pid) {
		if(pid == null) throw new IllegalArgumentException("pid ist null");
		if(pid.length() == 0) throw new IllegalArgumentException("pid ist leer");
		_pid = pid;
	}

	@Override
	public SystemObject getObject(final DataModel dataModel) {
		return dataModel.getObject(_pid);
	}

	@Override
	public boolean matches(final SystemObject object) {
		return object.getPid().equals(_pid);
	}

	@Override
	public boolean equals(final Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		final PidSpecification other = (PidSpecification)o;

		return !(_pid != null ? !_pid.equals(other._pid) : other._pid != null);
	}

	@Override
	public int hashCode() {
		return _pid != null ? _pid.hashCode() : 0;
	}

	@Override
	public String toString() {
		return _pid;
	}
}
