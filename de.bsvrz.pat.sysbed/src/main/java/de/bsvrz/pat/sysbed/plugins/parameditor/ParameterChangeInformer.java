/*
 * Copyright 2007 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.pat.sysbed.
 * 
 * de.bsvrz.pat.sysbed is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.pat.sysbed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.pat.sysbed; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.bsvrz.pat.sysbed.plugins.parameditor;

import de.bsvrz.dav.daf.main.ResultData;

/**
 * TBD RS dokumentieren.
*
* @author Kappich Systemberatung
* @version $Revision: 5058 $
*/
public interface ParameterChangeInformer {

	public void setResultData(final ResultData resultdata);

	public void setSourceObject(final String name);

	public void setSourceType(final String type);

	public void showSourceButton(boolean show);
}
