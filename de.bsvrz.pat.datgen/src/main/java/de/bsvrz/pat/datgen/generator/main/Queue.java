/*
 * Copyright 2007 by Kappich Systemberatung, Aachen
 * Copyright 2003 by Kappich+Kni� Systemberatung Aachen (K2S)
 * 
 * This file is part of de.bsvrz.pat.datgen.
 * 
 * de.bsvrz.pat.datgen is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.pat.datgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.pat.datgen; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */


package de.bsvrz.pat.datgen.generator.main;

import java.util.LinkedList;

/**
 * Implementierung einer LIFO-Queue
 *
 * @author Kappich Systemberatung
 * @version $Revision: 5030 $
 */
	public final class Queue implements SendInterface {
		/** Die Queue */
		private LinkedList _queue= new LinkedList();

		/** Element in die LIFO-Liste schreiben
		 * @param o {@link Object}, welches in die LIFO-Liste geschrieben wird.
		 */
		public void push(Object o) {
			synchronized(_queue) {
				_queue.addFirst(o);
				_queue.notify();
			}
		}

		/** Element aus der LIFO-Liste entnehmen
		 * @return {@link Object}, welches aus der LIFO-Liste entfernt wurde
		 */
		public Object pop() {
			synchronized(_queue) {
				try {
					while(_queue.isEmpty()) _queue.wait();
				}
				catch(InterruptedException e) {
					return null;
				}
				return _queue.removeLast();
			}
		}
	}


