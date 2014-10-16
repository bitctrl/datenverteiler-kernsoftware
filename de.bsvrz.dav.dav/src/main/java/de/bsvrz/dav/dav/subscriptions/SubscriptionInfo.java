/*
 * Copyright 2013 by Kappich Systemberatung Aachen
 * 
 * This file is part of de.bsvrz.dav.dav.
 * 
 * de.bsvrz.dav.dav is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * de.bsvrz.dav.dav is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with de.bsvrz.dav.dav; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package de.bsvrz.dav.dav.subscriptions;

import de.bsvrz.dav.daf.communication.lowLevel.telegrams.ApplicationDataTelegram;
import de.bsvrz.dav.daf.communication.lowLevel.telegrams.BaseSubscriptionInfo;
import de.bsvrz.dav.dav.main.ConnectionState;
import de.bsvrz.dav.dav.main.SubscriptionsManager;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Diese Klasse kapselt eine Anmeldungsinformation zu einer Objekt/Attributgruppe/Aspekt/Simulationsvariante-Kombination. Enthalten sind die eigentlichen
 * Anmeldungen von Applikationen und Datenverteilern auf diese BaseSubscriptionInfo. Diese Klasse k�mmert sich darum,
 * die Anmeldungen zu verwalten und je nach Verf�gbarkeit von Sendern, Empf�ngern, Quellen und Senken und je nach vorhandenen Rechten den einzelnen
 * Verbindungen per Sendesteuerung oder leeren Datens�tzen den Zustand der Anmeldung zu �bermitteln. Zus�tzlich �bernimmt diese Klasse das Verteilen von
 * Datens�tzen als Zentraldatenverteiler.
 *
 * @author Kappich Systemberatung
 * @version $Revision: 11476 $
 */
public class SubscriptionInfo implements Closeable {

	/**
	 * Liste mit Anmeldungen
	 */
	private final SubscriptionList _subscriptionList = new SubscriptionList();

	/**
	 * Soll versucht werden, sich zu einem entfernten Datenverteiler zu verbinden? (True wenn keine lokale Quelle oder Senke vorhanden ist)
	 */
	private boolean _connectToRemoteCentralDistributor = false;

	/**
	 * Referenz auf die Anmeldungsverwaltung
	 */
	private final SubscriptionsManager _subscriptionsManager;

	/**
	 * Datenidentifikation
	 */
	private final BaseSubscriptionInfo _baseSubscriptionInfo;

	/**
	 * Zwischenspeicher f�r die zuletzt gesendeten Telegramme einer Quelle
	 */
	private List<ApplicationDataTelegram> _lastSendTelegrams = null;

	/**
	 * Letzter gesendeter/weitergeleiteter Datenindex (1 = kein oder nur ein k�nstlicher Datensatz vorher gesendet).
	 * Die eigentliche Datenindexgenerierung im Zentraldatenverteiler findet in der {@link SubscriptionList}-Klasse statt.
	 */
	private long _lastSendDataIndex = 1;

	/**
	 * Sind Anmeldungen gesperrt, weil es mehrere Remote-Datenverteiler mit positiven R�ckmeldungen gibt?
	 */
	private boolean _multiRemoteLockActive = false;

	/**
	 * Ist true w�hrend die Remote-Anmeldungen aktualisiert werden. Verhindert, dass {@link #setConnectToRemoteCentralDistributor(boolean)} rekursiv aufgerufen wird,
	 * wodurch st�rende Effekte entstehen k�nnen.
	 */
	private boolean _remoteUpdateLockActive = false;

	/**
	 * Laufende Anmeldeumleitungen, enthalt eine Map mit Zuordnung ZentralverteilerId->Neue Verbindung. In dieser Map sind die neuen Verbindungen gespeichert, w�hrend
	 * sie noch aufgebaut werden. Nachdem die verbindung erfolgreich aufgebaut wurde, wird dann die eigentliche Anmeldung in der {@link SubscriptionList}
	 * umgebogen und der eintrag aus dieser Map entfernt.
	 */
	private final HashMap<Long, PendingSubscription> _pendingSubscriptions = new HashMap<Long, PendingSubscription>();

	private int _referenceCounter = 0;

	/**
	 * Erstellt eine neue SubscriptionInfo
	 * @param subscriptionsManager Anmeldungsverwaltung
	 * @param baseSubscriptionInfo Datenidentifikation
	 */
	public SubscriptionInfo(final SubscriptionsManager subscriptionsManager, final BaseSubscriptionInfo baseSubscriptionInfo) {
		_subscriptionsManager = subscriptionsManager;
		_baseSubscriptionInfo = baseSubscriptionInfo;
	}

	/**
	 * F�gt eine sendende Anmeldung hinzu
	 * @param sendingSubscription neue sendende Anmeldung
	 */
	public synchronized void addSendingSubscription(final SendingSubscription sendingSubscription) {
		_subscriptionList.addSender(sendingSubscription);
		refreshSubscriptionsOnNewSender(sendingSubscription);
	}

	/**
	 * F�gt eine empfangende Anmeldung hinzu
	 * @param receivingSubscription neue empfangende Anmeldung
	 */
	public synchronized void addReceivingSubscription(final ReceivingSubscription receivingSubscription) {
		_subscriptionList.addReceiver(receivingSubscription);
		refreshSubscriptionsOnNewReceiver(receivingSubscription);
	}

	/**
	 * Aktualisiert die Anmeldungszust�nde wenn ein neuer Sender/eine neue Quelle angemeldet wird
	 * @param sendingSubscription neue sendende Anmeldung
	 */
	private void refreshSubscriptionsOnNewSender(final SendingSubscription sendingSubscription) {
		if(!sendingSubscription.getConnectionState().isValid()) return;
		if(_multiRemoteLockActive) {
			sendingSubscription.setState(SenderState.MULTIPLE_REMOTE_LOCK, getCentralDistributorId());
			return;
		}
		if(!sendingSubscription.isAllowed()) {
			sendingSubscription.setState(SenderState.NOT_ALLOWED, getCentralDistributorId());
			return;
		}
		if(sendingSubscription.isSource()) {
			if(isLocalSubscription(sendingSubscription)) setConnectToRemoteCentralDistributor(false);
			if(_subscriptionList.canSetSource(sendingSubscription)) {
				setSource(sendingSubscription);
			}
			else {
				sendingSubscription.setState(SenderState.INVALID_SUBSCRIPTION, getCentralDistributorId());
				return;
			}
		}
		sendingSubscription.setState(SenderState.WAITING, getCentralDistributorId());
		updateSenderReceiverStatus();
	}


	/**
	 * Aktualisiert die Anmeldungszust�nde wenn eine neue Senke oder ein Empf�nger angemeldet wird
	 * @param receivingSubscription neue empfangende Anmeldung
	 */
	private void refreshSubscriptionsOnNewReceiver(final ReceivingSubscription receivingSubscription) {
		if(!receivingSubscription.getConnectionState().isValid()) return;
		if(_multiRemoteLockActive) {
			receivingSubscription.setState(ReceiverState.MULTIPLE_REMOTE_LOCK, getCentralDistributorId());
			return;
		}
		if(!receivingSubscription.isAllowed()) {
			receivingSubscription.setState(ReceiverState.NOT_ALLOWED, getCentralDistributorId());
			receivingSubscription.sendStateTelegram(ReceiverState.NOT_ALLOWED);
			return;
		}
		if(receivingSubscription.isDrain()) {
			if(isLocalSubscription(receivingSubscription)) setConnectToRemoteCentralDistributor(false);
			if(_subscriptionList.canSetDrain(receivingSubscription)) {
				setDrain(receivingSubscription);
			}
			else {
				receivingSubscription.setState(ReceiverState.INVALID_SUBSCRIPTION, getCentralDistributorId());
				receivingSubscription.sendStateTelegram(ReceiverState.INVALID_SUBSCRIPTION);
				return;
			}
		}
		receivingSubscription.setState(ReceiverState.WAITING, getCentralDistributorId());
		updateSenderReceiverStatus();
	}

	/**
	 * Aktualisiert den Anmeldestatus von den angemeldeten g�ltigen (d.h. nicht-verbotenen und nicht ung�ltigen) Anmeldungen
	 */
	private void updateSenderReceiverStatus() {
		updateRemoteConnectionsNecessary();

		if(hasPendingRemoteSubscriptions()) return;

		long centralDistributorId = getCentralDistributorId();
		final List<SendingSubscription> sendingSubscriptions = getValidSenderSubscriptions();
		final List<ReceivingSubscription> receivingSubscriptions = getValidReceiverSubscriptions();
		if(sendingSubscriptions.isEmpty()|| !_subscriptionList.hasDrainOrSource()) {
			// Es gibt keine Sender, oder es sind nur Sender und Empf�nger vorhanden
			// -> Statusmeldung "Keine Quelle" an Empf�nger
			for(ReceivingSubscription subscription : receivingSubscriptions) {
				ReceiverState prevState = subscription.getState();
				if(prevState != ReceiverState.NO_SENDERS){
					subscription.setState(ReceiverState.NO_SENDERS, centralDistributorId);
					// hier wird "keine Quelle" an Senke gesendet
					// das ist nur notwendig, wenn vorher noch kein "keine Quelle" gesendet wurde
					// Da "keine Quelle" auch im Status SENDERS_AVAILABLE gesendet wird (siehe weiter unten)
					// ist hier die Pr�fung notwendig um keine unn�tigen Datens�tze zu versenden.
					if(prevState != ReceiverState.SENDERS_AVAILABLE) {
						subscription.sendStateTelegram(ReceiverState.NO_SENDERS);
					}
				}
			}
			// -> Sendesteuerung keine Empf�nger an evtl. vorhandene Sender
			for(SendingSubscription sendingSubscription : sendingSubscriptions) {
				sendingSubscription.setState(SenderState.NO_RECEIVERS, centralDistributorId);
			}
		}
		else if(receivingSubscriptions.isEmpty()){
			// -> Sendesteuerung keine Empf�nger an evtl. vorhandene Quellen/Sender
			for(SendingSubscription sendingSubscription : sendingSubscriptions) {
				sendingSubscription.setState(SenderState.NO_RECEIVERS, centralDistributorId);
			}
		}
		else {
			// Es gibt Quelle und Empf�nger oder Senke und Sender

			// Falls es eine Quelle gibt, den evtl. gespeicherten Datensatz an alle Empf�nger weiterleiten.
			if(hasSource()) {
				for(ReceivingSubscription subscription : receivingSubscriptions) {
					if(subscription.getState() != ReceiverState.SENDERS_AVAILABLE){
						subscription.setState(ReceiverState.SENDERS_AVAILABLE, centralDistributorId);
						if(_lastSendTelegrams != null){
							for(final ApplicationDataTelegram telegram : _lastSendTelegrams) {
								subscription.sendDataTelegram(telegram);
							}
						}
					}
				}
			}
			else {
				// Falls es eine Senke gibt, Senke benachrichtigen, dass alles funktioniert hat (NO_SENDERS senden),
				// das gleiche mit Empf�ngern machen, die neben einer Senke angemeldet wurden
				// Wenn es weder eine Quelle oder Senke gibt, ebenfalls den Status auf NO_SENDERS setzen
				for(ReceivingSubscription subscription : receivingSubscriptions) {
					ReceiverState prevState = subscription.getState();
					if(prevState != ReceiverState.SENDERS_AVAILABLE){
						subscription.setState(ReceiverState.SENDERS_AVAILABLE, centralDistributorId);
						// hier wird "keine Quelle" an Senke gesendet, obwohl Sender vorhanden sind
						// das ist notwendig, weil es kein "Es gibt Sender"-telegramm gibt bzw. eine Senke sich nicht
						// daf�r interessieren sollte ob es sender gibt oder nicht.
						if(prevState != ReceiverState.NO_SENDERS) {
							subscription.sendStateTelegram(ReceiverState.NO_SENDERS);
						}
					}
				}
			}

			for(final SendingSubscription sendingSubscription : sendingSubscriptions) {
				sendingSubscription.setState(SenderState.RECEIVERS_AVAILABLE, centralDistributorId);
			}
		}
	}

	/**
	 * Gibt zur�ck, ob noch Anmeldungen bei anderen Datenverteilern laufen und daher derzeit keine Aktualisierungen von Anmeldungen erfolgen sollten.
	 * Zum beispiel wein ein lokaler Empf�nger angemeldet wird liefert diese Funktion true zur�ck, bis es entweder eine lokale Quelle gibt,
	 * ein Datenverteiler die Anmeldung positiv quittiert hat, oder alle in Frage kommenden Datenverteiler eine negative Quittung gesendet haben.
	 * @return true wenn derzeit noch Anmeldungen im Gange sind und es keine positive R�ckmeldung gibt.
	 */
	private boolean hasPendingRemoteSubscriptions() {
		int numWaiting = 0;
		int numPositive = 0;
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(!(sendingSubscription instanceof RemoteSourceSubscription)) continue;
			if(sendingSubscription.getConnectionState() == ConnectionState.TO_REMOTE_WAITING) numWaiting++;
			else if(sendingSubscription.getConnectionState().isValid()) numPositive++;
		}
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(!(receivingSubscription instanceof RemoteDrainSubscription)) continue;
			if(receivingSubscription.getConnectionState() == ConnectionState.TO_REMOTE_WAITING) numWaiting++;
			else if(receivingSubscription.getConnectionState().isValid()) numPositive++;
		}
		return numPositive == 0 && numWaiting > 0;
	}


	/**
	 * Aktualisiert den Anmeldestatus von allen Anmeldungen wenn sich ein Sender abmeldet
	 * @param toRemove Abmeldender Sender/Quelle
	 */
	private void refreshSubscriptionsOnSenderRemoval(final SendingSubscription toRemove) {
		long centralDistributorId = getCentralDistributorId();
		List<SendingSubscription> validSenderSubscriptions = getValidSenderSubscriptions();
		if(validSenderSubscriptions.size() == 0) {
			for(final ReceivingSubscription receivingSubscription : getValidReceiverSubscriptions()) {
				receivingSubscription.setState(ReceiverState.NO_SENDERS, centralDistributorId);
				if(!_subscriptionList.hasDrain()){
					// Senken werden nicht informiert, wenn der letzte Sender sich abgemeldet hat, das gleiche trifft auf daneben angemeldete Empf�nger zu.
					receivingSubscription.sendStateTelegram(ReceiverState.NO_SENDERS);
				}
			}
		}

		updateSenderReceiverStatus();
		if(toRemove == _subscriptionList.getSource()) setSource(null);
		refreshSubscriptions(toRemove);

		// updateReceiverStatus am Ende aufrufen, damit die Quelle dann schon entfernt ist und potentiell bei anderen Datenverteilern
		// nach Quellen gesucht wird
		updateSenderReceiverStatus();
	}

	/**
	 * Aktualisiert den Anmeldestatus von allen Anmeldungen wenn sich eine empfangende Anmeldung abmeldet
	 * @param toRemove Abmeldender Empf�nger/Senke
	 */
	private void refreshSubscriptionsOnReceiverRemoval(final ReceivingSubscription toRemove) {
		long centralDistributorId = getCentralDistributorId();
		List<ReceivingSubscription> validReceiverSubscriptions = getValidReceiverSubscriptions();
		if(validReceiverSubscriptions.size() == 0) {
			for(final SendingSubscription sendingSubscription : getValidSenderSubscriptions()) {
				sendingSubscription.setState(SenderState.NO_RECEIVERS, centralDistributorId);
			}
		}

		updateSenderReceiverStatus();
		if(toRemove == _subscriptionList.getDrain()) setDrain(null);
		refreshSubscriptions(toRemove);

		// updateReceiverStatus am Ende aufrufen, damit die Quelle dann schon entfernt ist und potentiell bei anderen Datenverteilern
		// nach Quellen gesucht wird
		updateSenderReceiverStatus();
	}

	/**
	 * Aktualisiert den Anmeldestatus von bisherigen Anmeldungen. Wenn z.B. eine Senke abgemeldet wird,
	 * wird hier versucht, eventuelle andere Senken oder Quellen zu "aktivieren" (auf g�ltig zu setzen)
	 * @param toIgnore Anmeldung die gerade abgemeldet wird und folglich eben nicht aktiviert werden soll
	 */
	private void refreshSubscriptions(final Subscription toIgnore) {
		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription != toIgnore && sendingSubscription.getState() == SenderState.INVALID_SUBSCRIPTION){
				refreshSubscriptionsOnNewSender(sendingSubscription);
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription != toIgnore && receivingSubscription.getState() == ReceiverState.INVALID_SUBSCRIPTION) {
				refreshSubscriptionsOnNewReceiver(receivingSubscription);
			}
		}
	}

	/**
	 * Pr�ft ob mehrere Remote-Zentraldatenverteiler eine positive R�ckmeldung auf eine Datenanmeldung gesendet haben. Falls ja,
	 * entsteht ein ung�ltiger Zustand, welcher durch {@link #_multiRemoteLockActive} dargestellt wird.
	 */
	private void updateMultiRemoteConnectionsLock() {
		setMultiRemoteLockActive(getMultipleRemoteConnectionsSubscribed());
	}

	/**
	 * Pr�ft ob mehrere Remote-Zentraldatenverteiler eine positive R�ckmeldung auf eine Datenanmeldung gesendet haben.
	 * @return true fall es mehrere Positive R�ckmeldungen gibt.
	 */
	private boolean getMultipleRemoteConnectionsSubscribed() {
		int numRemoteSubscriptions = 0;
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteCentralSubscription) {
				RemoteCentralSubscription subscription = (RemoteCentralSubscription)sendingSubscription;
				ConnectionState connectionState = subscription.getConnectionState();
				if(connectionState == ConnectionState.TO_REMOTE_MULTIPLE) return true;
				if(connectionState.isValid()){
					numRemoteSubscriptions++;
				}
			}
		}
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteCentralSubscription) {
				RemoteCentralSubscription subscription = (RemoteCentralSubscription)receivingSubscription;
				ConnectionState connectionState = subscription.getConnectionState();
				if(connectionState == ConnectionState.TO_REMOTE_MULTIPLE) return true;
				if(connectionState.isValid()){
					numRemoteSubscriptions++;
				}
			}
		}
		return numRemoteSubscriptions > 1;
	}

	/**
	 * Pr�ft, ob Anmeldungen zu anderen Zentraldatenverteilern versendet werden sollen und f�hrt diese Anmeldungen durch. Das ist der Fall, wenn es lokale Sender oder
	 * Empf�nger-Anmeldungen gibt, aber der aktuelle Datenverteiler nicht der Zentraldatenverteiler ist.
	 */
	private void updateRemoteConnectionsNecessary() {
		setConnectToRemoteCentralDistributor(needsToConnectToRemoteCentralDav());
		removeNegativeRemoteSubscriptions();
	}

	/**
	 * Gibt zur�ck, ob versucht werden soll, sich an einem anderen ZentralDatenverteiler anzumelden.
	 * <p>
	 * Das ist der Fall, falls es sich lokal um keinen ZentralDatenverteiler handelt, also keine lokale Quelle oder Senke angemeldet ist,
	 * und es aber g�ltige Sender oder Empf�nger-Anmeldungen gibt.
	 * </p>
	 *
	 * @return Ob versucht werden soll, sich an einem entfernten Zentraldatenverteiler anzumelden, bzw. ob solche Verbindung aufrecht erhalten werden
	 */
	private boolean needsToConnectToRemoteCentralDav() {
		if(isCentralDistributor()) return false;
		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(!sendingSubscription.isSource()) {
				SenderState state = sendingSubscription.getState();
				if(state.isValidSender() || state == SenderState.MULTIPLE_REMOTE_LOCK) {
					return true;
				}
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(!receivingSubscription.isDrain()) {
				ReceiverState state = receivingSubscription.getState();
				if(state.isValidReceiver() || state == ReceiverState.MULTIPLE_REMOTE_LOCK) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Setzt, ob Anmeldungen bei entfernten Datenverteilern durhgef�hrt werden sollen und f�hrt die An- bzw. Abmeldungen durch.
	 * @param newValue Soll zu anderen Zentraldatenverteilern verbunden werden?
	 */
	private void setConnectToRemoteCentralDistributor(final boolean newValue) {
		if(_remoteUpdateLockActive) return;
		_remoteUpdateLockActive = true;
		try {
			if(_connectToRemoteCentralDistributor == newValue) return;
			_connectToRemoteCentralDistributor = newValue;
			if(newValue) {
				// Sich bei entfernten Zentraldatenverteilern anmelden (falls vorhanden)
				createRemoteCentralDistributorSubscriptions();
			}
			else {
				// Sich bei entfernten Zentraldatenverteilern abmelden (falls vorhanden)
				removeRemoteSubscriptions();
			}
		}
		finally {
			_remoteUpdateLockActive = false;
		}
	}

	/**
	 * F�hrt Anmeldungen bei anderen Datenverteilern durch
	 */
	private void createRemoteCentralDistributorSubscriptions() {
		if(hasSource() || hasDrain()) return;

		// Potentielle Zentraldatenverteiler ermitteln
		final List<Long> distributors = _subscriptionsManager.getPotentialCentralDistributors(_baseSubscriptionInfo);
		if(distributors == null || distributors.size() == 0) return;

		List<ReceivingSubscription> validReceiverSubscriptions = getValidReceiverSubscriptions();
		List<SendingSubscription> validSenderSubscriptions = getValidSenderSubscriptions();

		// Zu ber�cksichtigende Datenverteiler
		final Set<Long> distributorsToUse = new HashSet<Long>();

		for(SendingSubscription sendingSubscription : validSenderSubscriptions) {
			if(sendingSubscription instanceof RemoteSubscription) {
				// Datenverteiler ber�cksichtigen, die bei eingehenden Anmeldungen angegeben wurden
				RemoteSubscription remoteSubscription = (RemoteSubscription)sendingSubscription;
				Set<Long> transmitterList = remoteSubscription.getPotentialDistributors();
				for(long l : transmitterList) {
					distributorsToUse.add(l);
				}
			}

			else {
				// F�r lokale Anmeldungen alle m�glichen Datenverteiler ber�cksichtigen
				distributorsToUse.addAll(distributors);
			}
		}
		for(ReceivingSubscription receivingSubscription : validReceiverSubscriptions) {
			if(receivingSubscription instanceof RemoteSubscription) {
				// Datenverteiler ber�cksichtigen, die bei eingehenden Anmeldungen angegeben wurden
				RemoteSubscription remoteSubscription = (RemoteSubscription)receivingSubscription;
				Set<Long> transmitterList = remoteSubscription.getPotentialDistributors();
				for(long l : transmitterList) {
					distributorsToUse.add(l);
				}
			}
			else {
				// F�r lokale Anmeldungen alle m�glichen Datenverteiler ber�cksichtigen
				distributorsToUse.addAll(distributors);
			}
		}

		if(validReceiverSubscriptions.size() > 0) {
			// wenn Empf�nger vorhanden, nach Quellen suchen
			_subscriptionsManager.connectToRemoteSources(this, distributorsToUse);
		}
		if(validSenderSubscriptions.size() > 0) {
			// wenn Sender vorhanden, nach Senken suchen
			_subscriptionsManager.connectToRemoteDrains(this, distributorsToUse);
		}
	}

	/**
	 * Entfernt alle Anmeldungen bei entfernten Zentraldatenverteilern
	 */
	private void removeRemoteSubscriptions() {
		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteCentralSubscription) {
				final RemoteSubscription subscription = (RemoteSubscription)sendingSubscription;
				subscription.unsubscribe();
				removeSendingSubscription(sendingSubscription);
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteCentralSubscription) {
				final RemoteSubscription subscription = (RemoteSubscription)receivingSubscription;
				subscription.unsubscribe();
				removeReceivingSubscription(receivingSubscription);
			}
		}
	}

	/**
	 * Meldet �berfl�ssige Anmeldungen bei Remote-Datenverteilern ab. Anmeldungen sind �berfl�ssig, wenn es genau eine andere Anmeldung mit positiver R�ckmeldung
	 * gibt und der Datenverteiler dieser Anmeldung signalisiert hat, dass er nicht zust�ndig ist.
	 */
	private void removeNegativeRemoteSubscriptions() {
		if(!_connectToRemoteCentralDistributor) return;

		// Nur negative Anmeldungen entfernen wenn es genau eine positive Anmeldung gibt. Dazu positive und negative Anmeldungen z�hlen
		int numPositiveResponses = 0;
		int numNegativeResponses = 0;
		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteCentralSubscription) {
				if(sendingSubscription.getConnectionState().isValid()) {
					numPositiveResponses++;
				}
				else {
					numNegativeResponses++;
				}
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteCentralSubscription) {
				if(receivingSubscription.getConnectionState().isValid()){
					numPositiveResponses++;
				}
				else {
					numNegativeResponses++;
				}
			}
		}

		// Falls es genau eine positive Anmeldung und mehr als eine negative Anmeldung gibt, die negativen Anmeldungen entfernen
		if(numNegativeResponses == 0 || numPositiveResponses != 1) return;

		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteCentralSubscription) {
				if(sendingSubscription.getConnectionState() == ConnectionState.TO_REMOTE_NOT_RESPONSIBLE) {
					final RemoteSubscription subscription = (RemoteSubscription)sendingSubscription;
					subscription.unsubscribe();
					removeSendingSubscription(sendingSubscription);
				}
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteCentralSubscription) {
				if(receivingSubscription.getConnectionState() == ConnectionState.TO_REMOTE_NOT_RESPONSIBLE){
					final RemoteSubscription subscription = (RemoteSubscription)receivingSubscription;
					subscription.unsubscribe();
					removeReceivingSubscription(receivingSubscription);
				}
			}
		}
	}

	/**
	 * Entfernt eine empfangende Anmeldung
	 * @param receivingSubscription empfangende Anmeldung
	 */
	public synchronized void removeReceivingSubscription(final ReceivingSubscription receivingSubscription) {
		receivingSubscription.setState(ReceiverState.UNKNOWN, getCentralDistributorId());
		refreshSubscriptionsOnReceiverRemoval(receivingSubscription);
		_subscriptionList.removeReceiver(receivingSubscription);
		receivingSubscription.unsubscribe();
	}

	/**
	 * Entfernt eine sendende Anmeldung
	 * @param sendingSubscription sendende Anmeldung
	 */
	public synchronized void removeSendingSubscription(final SendingSubscription sendingSubscription) {
		sendingSubscription.setState(SenderState.UNKNOWN, getCentralDistributorId());
		refreshSubscriptionsOnSenderRemoval(sendingSubscription);
		_subscriptionList.removeSender(sendingSubscription);
		sendingSubscription.unsubscribe();
	}

	/**
	 * Entfernt alle sendende Anmedungen, die �ber die angegebene Verbindung angemeldet sind
	 * @param communication Verbindung
	 * @return Liste mit entfernten Sendern und Quellen
	 */
	public synchronized List<SendingSubscription> removeSendingSubscriptions(final CommunicationInterface communication) {
		final List<SendingSubscription> result = new ArrayList<SendingSubscription>();
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription.getCommunication() == communication) {
				removeSendingSubscription(sendingSubscription);
				result.add(sendingSubscription);
			}
		}
		return result;
	}
	/**
	 * Entfernt alle empfangende Anmedungen, die �ber die angegebene Verbindung angemeldet sind
	 * @param communication Verbindung
	 * @return Liste mit entfernten Empf�ngern und Senken
	 */
	public synchronized List<ReceivingSubscription> removeReceivingSubscriptions(final CommunicationInterface communication) {
		final List<ReceivingSubscription> result = new ArrayList<ReceivingSubscription>();
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription.getCommunication() == communication) {
				removeReceivingSubscription(receivingSubscription);
				result.add(receivingSubscription);
			}
		}
		return result;
	}


	/**
	 * Gibt alle g�ltigen sendenden Anmeldungen zur�ck
	 * @return alle g�ltigen sendenden Anmeldungen (Quellen und Sender)
	 */
	public synchronized List<SendingSubscription> getValidSenderSubscriptions() {
		Collection<SendingSubscription> sendingSubscriptions = _subscriptionList.getSendingSubscriptions();
		final ArrayList<SendingSubscription> list = new ArrayList<SendingSubscription>(sendingSubscriptions.size());
		for(final SendingSubscription sendingSubscription : sendingSubscriptions) {
			if(sendingSubscription.getState().isValidSender()) list.add(sendingSubscription);
		}
		return list;
	}

	/**
	 * Gibt alle g�ltigen empfangenden Anmeldungen zur�ck
	 * @return alle g�ltigen empfangenden Anmeldungen (Senken und Empf�nger)
	 */
	public synchronized List<ReceivingSubscription> getValidReceiverSubscriptions() {
		Collection<ReceivingSubscription> receivingSubscriptions = _subscriptionList.getReceivingSubscriptions();
		final ArrayList<ReceivingSubscription> list = new ArrayList<ReceivingSubscription>(receivingSubscriptions.size());
		for(final ReceivingSubscription receivingSubscription : receivingSubscriptions) {
			if(receivingSubscription.getState().isValidReceiver()) list.add(receivingSubscription);
		}
		return list;
	}

	/**
	 * Gibt <tt>true</tt> zur�ck, wenn es keine Anmeldungen gibt
	 * @return <tt>true</tt>, wenn es keine Anmeldungen gibt, sonst <tt>false</tt>
	 */
	public synchronized boolean isEmpty() {
		return _subscriptionList.isEmpty();
	}

	/**
	 * Gibt <tt>true</tt> zur�ck, wenn dieser Datenverteiler Zentraldatenverteiler f�r diese Anmeldugn ist
	 * @return <tt>true</tt>, wenn dieser Datenverteiler Zentraldatenverteiler f�r diese Anmeldugn ist, sonst <tt>false</tt>
	 */
	public synchronized boolean isCentralDistributor() {
		return _subscriptionList.isCentralDistributor();
	}

	/**
	 * Berechnet den n�chsten Datenindex und gibt diesen zur�ck
	 * @return n�chsten Datenindex, "0" falls dieser Datenverteiler nicht der Zentraldatenverteiler ist.
	 * @param runningNumber Laufende Nummer, wird vom SubscriptionsManager bereitgestellt, da diese Objekte gel�scht werden sobas keine Anmeldungen mehr vorhanden sind
	 */
	public synchronized long getNextDataIndex(final long runningNumber) {
		return _subscriptionList.getDataIndex(runningNumber);
	}

	/**
	 * Gibt den zuletzt berechneten Datenindex zur�ck
	 * @return zuletzt berechneten Datenindex, "0" falls dieser Datenverteiler nicht der Zentraldatenverteiler ist.
	 * @param runningNumber Laufende Nummer, wird vom SubscriptionsManager bereitgestellt, da diese Objekte gel�scht werden sobas keine Anmeldungen mehr vorhanden sind
	 */
	public synchronized long getCurrentDataIndex(final long runningNumber) {
		return _subscriptionList.getDataIndex(runningNumber);
	}

	/**
	 * Verschickt ein einzelnes Datentelegramm an alle interessierten und korrekt angemeldeten Empf�nger
	 * @param applicationDataTelegram Datentelegramm
	 * @param toCentralDistributor wenn das Telegramm noch nicht beim Zentraldatenverteiler behandelt wurde, also der Datenindex noch nicht vern�nftig gesetzt wurde
	 */
	public void distributeTelegram(final ApplicationDataTelegram applicationDataTelegram, final boolean toCentralDistributor) {
		distributeTelegrams(Collections.singletonList(applicationDataTelegram), toCentralDistributor);
	}

	/**
	 * Verschickt eine Liste von zusammengeh�rigen Datentelegrammen an alle interessierten und korrekt angemeldeten Empf�nger
	 * @param applicationDataTelegrams Datentelegramme
	 * @param toCentralDistributor wenn das Telegramm noch nicht beim Zentraldatenverteiler behandelt wurde, also der Datenindex noch nicht vern�nftig gesetzt wurde
	 */
	public synchronized void distributeTelegrams(final List<ApplicationDataTelegram> applicationDataTelegrams, final boolean toCentralDistributor) {
		final List<ReceivingSubscription> receivingSubscriptions = getValidReceiverSubscriptions();

		long dataIndex = applicationDataTelegrams.get(0).getDataNumber();

		if(!toCentralDistributor && _lastSendDataIndex > 1 && dataIndex <= _lastSendDataIndex){
			// Kein monoton steigender Datenindex
			return;
		}

		for(final ReceivingSubscription receivingSubscription : receivingSubscriptions) {
			if(!receivingSubscription.getReceiveOptions().withDelayed() && applicationDataTelegrams.get(0).getDelayedDataFlag()) {
				// Datensatz ist als nachgeliefert markiert, der Empf�nger will aber nur aktuelle Daten
				continue;
			}
			if(receivingSubscription.getReceiveOptions().withDelta() && telegramsAreEqual(applicationDataTelegrams, _lastSendTelegrams)) {
				// Datensatz ist unver�ndert, der Empf�nger will aber nur ge�nderte Daten
				continue;
			}
			for(final ApplicationDataTelegram telegram : applicationDataTelegrams) {
				receivingSubscription.sendDataTelegram(telegram);
			}
		}

		if(hasSource() && !applicationDataTelegrams.get(0).getDelayedDataFlag()) {
			if(dataIndex != 1) _lastSendDataIndex = dataIndex;
			_lastSendTelegrams = new ArrayList<ApplicationDataTelegram>(applicationDataTelegrams);
		}
	}

	public synchronized void updatePendingSubscriptionDataIndex(final CommunicationInterface communication, final long dataIndex) {
		for(Map.Entry<Long, PendingSubscription> entry : _pendingSubscriptions.entrySet()) {
			PendingSubscription pendingSubscription = entry.getValue();
			if(pendingSubscription.getNewSubscription().getCommunication() == communication){
				pendingSubscription.setLastReceivedDataIndex(dataIndex);
				handlePendingSubscriptions(entry.getKey(),
				                           (TransmitterCommunicationInterface)communication,
				                           pendingSubscription.getNewSubscription().getConnectionState());
			}
		}
	}

	/**
	 * Pr�ft ob 2 Telegrammlisten im Sinne der Anmeldung auf Delta-Datens�tze gleich sind.
	 *
	 * @param telegrams1 Daten-Telegramme 1
	 * @param telegrams2 Daten-Telegramme 2
	 *
	 * @return True wenn Daten vorhanden und identisch sind
	 */
	private boolean telegramsAreEqual(final List<ApplicationDataTelegram> telegrams1, final List<ApplicationDataTelegram> telegrams2) {
		if(telegrams1 == null || telegrams2 == null) return false;

		if(telegrams1.size() != telegrams2.size()) return false;

		if(telegrams1.get(0).getErrorFlag() != 0 || telegrams2.get(0).getErrorFlag() != 0) return false;

		for(int i = 0, size = telegrams1.size(); i < size; i++) {
			final ApplicationDataTelegram telegram1 = telegrams1.get(i);
			final ApplicationDataTelegram telegram2 = telegrams2.get(i);
			if(!Arrays.equals(telegram1.getData(), telegram2.getData())) return false;
		}
		return true;
	}

	/**
	 * Aktualisert die Rechte von Anmeldungen und macht diese dadurch g�ltig/ung�ltig
	 * @param userId ge�nderter Benutzer, dessen Anmeldungen zu aktualisieren sind
	 */
	public synchronized void handleUserRightsChanged(final long userId) {
		for(final ReceivingSubscription subscription : _subscriptionList.getReceivingSubscriptions()) {
			if(subscription.getUserId() != userId) continue;
			final boolean isAllowed = subscription.isAllowed();
			if(isAllowed && subscription.getState() == ReceiverState.NOT_ALLOWED) {
				// Anmeldung wird g�ltig, Anmeldung "hinzuf�gen" und Sender informieren
				refreshSubscriptionsOnNewReceiver(subscription);
			}
			else if(!isAllowed && subscription.getState() != ReceiverState.NOT_ALLOWED) {
				// Anmeldung wird ung�ltig
				subscription.setState(ReceiverState.NOT_ALLOWED, getCentralDistributorId());
				subscription.sendStateTelegram(ReceiverState.NOT_ALLOWED);
				refreshSubscriptionsOnReceiverRemoval(subscription);
			}
		}

		for(final SendingSubscription subscription : _subscriptionList.getSendingSubscriptions()) {
			if(subscription.getUserId() != userId) continue;
			final boolean isAllowed = subscription.isAllowed();
			if(isAllowed && subscription.getState() == SenderState.NOT_ALLOWED) {
				// Anmeldung wird g�ltig, Anmeldung "hinzuf�gen" und Empf�nger informieren
				refreshSubscriptionsOnNewSender(subscription);
			}
			else if(!isAllowed && subscription.getState() != SenderState.NOT_ALLOWED) {
				// Anmeldung wird ung�ltig
				subscription.setState(SenderState.NOT_ALLOWED, getCentralDistributorId());
				refreshSubscriptionsOnSenderRemoval(subscription);
			}
		}
	}

	/**
	 * Verarbeitet eine Anmeldungsquittung von einem anderen Datenverteiler, aktualisert den Status der entsprechenden ausgehenden Anmeldung
	 * @param communication Kommunikation
	 * @param state neuer Status
	 * @param mainTransmitterId Id des Zentraldatenverteilers
	 */
	public synchronized void setRemoteSourceSubscriptionStatus(
			final TransmitterCommunicationInterface communication, final ConnectionState state, final long mainTransmitterId) {
		handlePendingSubscriptions(mainTransmitterId, communication, state);
		RemoteSourceSubscription remoteSubscription = null;
		for(final SendingSubscription subscription : _subscriptionList.getSendingSubscriptions()) {
			if(subscription instanceof RemoteSourceSubscription) {
				final RemoteSourceSubscription remoteSourceSubscription = (RemoteSourceSubscription)subscription;
				if(remoteSourceSubscription.getCommunication() == communication) {
					remoteSubscription = remoteSourceSubscription;
					break;
				}
			}
		}
		if(remoteSubscription == null) {
			return;
		}
		remoteSubscription.setRemoteState(mainTransmitterId, state);
		updateMultiRemoteConnectionsLock();
		if(remoteSubscription.getConnectionState().isValid() && !remoteSubscription.getState().isValidSender()) {
			// Anmeldung wird g�ltig
			refreshSubscriptionsOnNewSender(remoteSubscription);
		}
		else if(!remoteSubscription.getConnectionState().isValid() && remoteSubscription.getState().isValidSender()) {
			// Anmeldung wird ung�ltig
			remoteSubscription.setState(SenderState.NO_REMOTE_SOURCE, getCentralDistributorId());
			refreshSubscriptionsOnSenderRemoval(remoteSubscription);
		}
		updateSenderReceiverStatus();
		removeNegativeRemoteSubscriptions();
	}

	/**
	 * Verarbeitet eine Anmeldungsquittung von einem anderen Datenverteiler, aktualisert den Status der entsprechenden ausgehenden Anmeldung
	 * @param communication Kommunikation
	 * @param state neuer Status
	 * @param mainTransmitterId Id des Zentraldatenverteilers
	 */
	public synchronized void setRemoteDrainSubscriptionStatus(
			final TransmitterCommunicationInterface communication, final ConnectionState state, final long mainTransmitterId) {
		handlePendingSubscriptions(mainTransmitterId, communication, state);
		RemoteDrainSubscription remoteSubscription = null;
		for(final ReceivingSubscription subscription : _subscriptionList.getReceivingSubscriptions()) {
			if(subscription instanceof RemoteDrainSubscription) {
				final RemoteDrainSubscription remoteDrainSubscription = (RemoteDrainSubscription)subscription;
				if(remoteDrainSubscription.getCommunication() == communication) {
					remoteSubscription = remoteDrainSubscription;
					break;
				}
			}
		}
		if(remoteSubscription == null) {
			return;
		}
		remoteSubscription.setRemoteState(mainTransmitterId, state);
		updateMultiRemoteConnectionsLock();
		if(remoteSubscription.getConnectionState().isValid() && !remoteSubscription.getState().isValidReceiver()) {
			// Anmeldung wird g�ltig
			refreshSubscriptionsOnNewReceiver(remoteSubscription);
		}
		else if(!remoteSubscription.getConnectionState().isValid() && remoteSubscription.getState().isValidReceiver()) {
			// Anmeldung wird ung�ltig
			remoteSubscription.setState(ReceiverState.NO_REMOTE_DRAIN, getCentralDistributorId());
			refreshSubscriptionsOnReceiverRemoval(remoteSubscription);
		}
		updateSenderReceiverStatus();
		removeNegativeRemoteSubscriptions();
	}

	/**
	 * Aktualisiert Anmeldeumleitungen, ersetzt die alte Anmeldung falls Umleitung erfolgreich oder entfernt die neue Verbindung falls nicht erfolgreich.
	 * @param mainTransmitterId Zentraldatenverteiler-Id
	 * @param transmitterCommunicationInterface Kommunikation der neuen Anmeldung
	 * @param state neuer Status der Anmeldung
	 */
	private void handlePendingSubscriptions(
			final long mainTransmitterId, final TransmitterCommunicationInterface transmitterCommunicationInterface, final ConnectionState state) {

		// Gibt es Umleitungen?
		PendingSubscription pendingSubscriptionInfo = _pendingSubscriptions.get(mainTransmitterId);
		if(pendingSubscriptionInfo == null) return;
		RemoteCentralSubscription pendingSubscription = pendingSubscriptionInfo.getNewSubscription();

		if(pendingSubscription.getCommunication() == transmitterCommunicationInterface){

			// Neuen Status setzen
			pendingSubscription.setRemoteState(mainTransmitterId, state);

			if(pendingSubscription.getConnectionState().isValid()){

				if(pendingSubscription instanceof SendingSubscription
				   && pendingSubscriptionInfo.getLastReceivedDataIndex() != _lastSendDataIndex
				   && pendingSubscriptionInfo.getLastReceivedDataIndex() - 1 != _lastSendDataIndex
				   && _lastSendDataIndex != 1) {
					// Datenindex noch nicht synchron, erstmal nichts tun
					return;
				}

				// Neue Anmeldung erfolgreich, alte Anmeldung(en) abmelden.
				if(pendingSubscription instanceof RemoteDrainSubscription) {
					RemoteDrainSubscription oldSubscription = null;
					for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
						if(receivingSubscription instanceof RemoteDrainSubscription) {
							RemoteDrainSubscription other = (RemoteDrainSubscription)receivingSubscription;
							if(other.getCommunication() != pendingSubscription.getCommunication() && other.getCentralDistributorId() == mainTransmitterId){
								oldSubscription = other;
								break;
							}
						}
					}
					replaceReceiver(oldSubscription, (RemoteDrainSubscription)pendingSubscription);
				} else if(pendingSubscription instanceof RemoteSourceSubscription){
					RemoteSourceSubscription oldSubscription = null;
					for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
						if(sendingSubscription instanceof RemoteSourceSubscription) {
							RemoteSourceSubscription other = (RemoteSourceSubscription)sendingSubscription;
							if(other.getCommunication() != pendingSubscription.getCommunication() && other.getCentralDistributorId() == mainTransmitterId){
								oldSubscription = other;
								break;
							}
						}
					}
					replaceSender(oldSubscription, (RemoteSourceSubscription)pendingSubscription);
				}
			}
			else {
				// Umleitung nicht erfolgreich, wieder abmelden
				pendingSubscription.unsubscribe();
			}

			// Umleitungseintrag entfernen
			_pendingSubscriptions.remove(mainTransmitterId);
		}
	}

	/**
	 * Ersetzt eine Anmeldung wegen einer Anmeldeumleitung
	 * @param oldSubscription alte Anmeldung
	 * @param newSubscription neue Anmeldung
	 */
	private void replaceReceiver(final RemoteDrainSubscription oldSubscription, final RemoteDrainSubscription newSubscription) {
		_subscriptionList.addReceiver(newSubscription);
		if(oldSubscription != null){
			if(_subscriptionList.getDrain() == oldSubscription){
				_subscriptionList.setDrain(newSubscription);
			}
			_subscriptionList.removeReceiver(oldSubscription);
			newSubscription.setState(oldSubscription.getState(), oldSubscription.getCentralDistributorId());
			oldSubscription.unsubscribe();
		}
	}

	/**
	 * Ersetzt eine Anmeldung wegen einer Anmeldeumleitung
	 * @param oldSubscription alte Anmeldung
	 * @param newSubscription neue Anmeldung
	 */
	private void replaceSender(final RemoteSourceSubscription oldSubscription, final RemoteSourceSubscription newSubscription) {
		_subscriptionList.addSender(newSubscription);
		if(oldSubscription != null){
			if(_subscriptionList.getSource() == oldSubscription){
				_subscriptionList.setSource(newSubscription);
			}
			_subscriptionList.removeSender(oldSubscription);
			newSubscription.setState(oldSubscription.getState(), oldSubscription.getCentralDistributorId());
			oldSubscription.unsubscribe();
		}
	}

	/**
	 * Gibt die Zentraldatenverteiler-ID zur�ck
	 * @return die Zentraldatenverteiler-ID, sofern bekannt. Sonst -1
	 */
	private long getCentralDistributorId() {
		return _subscriptionList.getCentralDistributorId();
	}

	/**
	 * Setzt eine neue Senke
	 * @param drain neue Senke
	 */
	private void setDrain(final ReceivingSubscription drain) {
		ReceivingSubscription oldDrain = _subscriptionList.getDrain();
		if(oldDrain == drain) return;
		_lastSendTelegrams = null;
		_lastSendDataIndex = 1;
		if(!isLocalSubscription(oldDrain) && isLocalSubscription(drain)) {
			_subscriptionsManager.notifyIsNewCentralDistributor(_baseSubscriptionInfo);
		}
		if(isLocalSubscription(oldDrain) && !isLocalSubscription(drain)) {
			_subscriptionsManager.notifyWasCentralDistributor(_baseSubscriptionInfo);
		}
		_subscriptionList.setDrain(drain);
	}

	/**
	 * setzt eine neue Quelle
	 * @param source neue Quelle
	 */
	private void setSource(final SendingSubscription source) {
		SendingSubscription oldSource = _subscriptionList.getSource();
		if(oldSource == source) return;
		_lastSendTelegrams = null;
		_lastSendDataIndex = 1;
		if(!isLocalSubscription(oldSource) && isLocalSubscription(source)) {
			_subscriptionsManager.notifyIsNewCentralDistributor(_baseSubscriptionInfo);
		}
		if(isLocalSubscription(oldSource) && !isLocalSubscription(source)) {
			_subscriptionsManager.notifyWasCentralDistributor(_baseSubscriptionInfo);
		}
		_subscriptionList.setSource(source);
	}

	/**
	 * Pr�ft ob eine Anmeldung lokal ist
	 * @param subscription Anmeldung
	 * @return true wenn lokal
	 */
	private static boolean isLocalSubscription(final Subscription subscription) {
		return subscription != null && subscription instanceof LocalSubscription;
	}

	/**
	 * Gibt das BaseSubscriptionInfo zur�ck
	 * @return das BaseSubscriptionInfo
	 */
	public BaseSubscriptionInfo getBaseSubscriptionInfo() {
		return _baseSubscriptionInfo;
	}

	/** Wird aufgerufen, wenn im ListsManager ein Update stattfand und so eventuell neue oder bessere Wege f�r die Remote-Anmeldungen existieren */
	public synchronized void updateRemoteConnections() {
		// Falls kein Bedarf an entfernten Anmeldungen besteht, nichts tun
		if(!_connectToRemoteCentralDistributor) return;

		
		for(final SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteCentralSubscription) {
				final RemoteCentralSubscription remoteCentralSubscription = (RemoteCentralSubscription)sendingSubscription;
				long centralDistributorId = remoteCentralSubscription.getCentralDistributorId();
				updateBestWay(centralDistributorId, remoteCentralSubscription.getCommunication(), _subscriptionsManager.getBestConnectionToRemoteDav(centralDistributorId));
			}
		}
		for(final ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteCentralSubscription) {
				final RemoteCentralSubscription remoteCentralSubscription = (RemoteCentralSubscription)receivingSubscription;
				long centralDistributorId = remoteCentralSubscription.getCentralDistributorId();
				updateBestWay(
						centralDistributorId,
						remoteCentralSubscription.getCommunication(),
						_subscriptionsManager.getBestConnectionToRemoteDav(centralDistributorId)
				);
			}
		}

		createRemoteCentralDistributorSubscriptions();
	}

	/**
	 * Gibt <tt>true</tt> zur�ck, wenn eine Quelle verbunden ist (entweder lokal oder �ber eine Transmitterverbindung)
	 * @return <tt>true</tt>, wenn eine Quelle verbunden ist, sonst <tt>false</tt>
	 */
	public synchronized boolean hasSource() {
		return _subscriptionList.hasSource();
	}

	/**
	 * Gibt <tt>true</tt> zur�ck, wenn eine Senke verbunden ist (entweder lokal oder �ber eine Transmitterverbindung)
	 * @return <tt>true</tt>, wenn eine Senke verbunden ist, sonst <tt>false</tt>
	 */
	public synchronized boolean hasDrain() {
		return _subscriptionList.hasDrain();
	}

	/**
	 * Pr�ft, ob die angegebene Kommunikationsklasse senden darf (also als g�ltiger Sender angemeldet ist)
	 * @param communication Kommunikation
	 * @return true wenn g�ltig
	 */
	public synchronized boolean isValidSender(final CommunicationInterface communication) {
		// Normale Sendeanmeldungen pr�fen
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription.getCommunication() == communication){
				return sendingSubscription.getState().isValidSender();
			}
		}
		return false;
	}

	/**
	 * Setzt, ob Anmeldung ung�ltig gemacht werden sollen, weil mehrere remot-zentraldatenverteiler positive R�ckmeldungen verschickt haben
	 * @param multiRemoteLockActive ob die Sperre {@link #_multiRemoteLockActive} aktiv sein soll.
	 */
	public void setMultiRemoteLockActive(final boolean multiRemoteLockActive) {
		if(multiRemoteLockActive == _multiRemoteLockActive) return;
		_multiRemoteLockActive = multiRemoteLockActive;
		if(multiRemoteLockActive){
			// Wenn aktiv alle Anmeldungen ung�ltig machen und entsprechend markieren
			setDrain(null);
			setSource(null);
			for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
				if(sendingSubscription.getState() == SenderState.NO_REMOTE_SOURCE) continue;
				if(sendingSubscription instanceof RemoteCentralSubscription){
					sendingSubscription.setState(SenderState.MULTIPLE_REMOTE_LOCK, -1);
				}
				else {
					sendingSubscription.setState(SenderState.MULTIPLE_REMOTE_LOCK, -1);
				}
			}
			for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
				if(receivingSubscription.getState() == ReceiverState.NO_REMOTE_DRAIN) continue;
				if(receivingSubscription instanceof RemoteCentralSubscription){
					receivingSubscription.setState(ReceiverState.MULTIPLE_REMOTE_LOCK, -1);
				}
				else {
					receivingSubscription.setState(ReceiverState.MULTIPLE_REMOTE_LOCK, -1);
					receivingSubscription.sendStateTelegram(ReceiverState.INVALID_SUBSCRIPTION);
				}
			}
		}
		else {
			// Wenn wieder inaktiv alle lokalen Anmeldungen neu g�ltig machen

			// Von bisherigen Zentraldatenverteilern abmelden um einen konsitenten Zustand und neue initiale Telegramme zu erhalten
			setConnectToRemoteCentralDistributor(false);

			// Alle lokalen Anmeldungen neu initialisieren
			for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
				if(sendingSubscription.getState() == SenderState.NO_REMOTE_SOURCE) continue;
				sendingSubscription.setState(SenderState.UNKNOWN, -1);
				refreshSubscriptionsOnNewSender(sendingSubscription);
			}
			for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
				if(receivingSubscription.getState() == ReceiverState.NO_REMOTE_DRAIN) continue;
				receivingSubscription.setState(ReceiverState.UNKNOWN, -1);
				refreshSubscriptionsOnNewReceiver(receivingSubscription);
			}
		}
	}


	public List<SendingSubscription> getSendingSubscriptions(final CommunicationInterface communicationInterface) {
		final List<SendingSubscription> result = new ArrayList<SendingSubscription>();
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription.getCommunication() == communicationInterface) result.add(sendingSubscription);
		}
		return result;
	}

	public List<ReceivingSubscription> getReceivingSubscriptions(final CommunicationInterface communicationInterface) {
		final List<ReceivingSubscription> result = new ArrayList<ReceivingSubscription>();
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription.getCommunication() == communicationInterface) result.add(receivingSubscription);
		}
		return result;
	}

	public Collection<SendingSubscription> getSendingSubscriptions() {
		return _subscriptionList.getSendingSubscriptions();

	}

	public Collection<ReceivingSubscription> getReceivingSubscriptions() {
		return _subscriptionList.getReceivingSubscriptions();
	}

	public synchronized void updateBestWay(
			final long transmitterId, final TransmitterCommunicationInterface oldConnection, final TransmitterCommunicationInterface newConnection) {
		updateBestWaySource(transmitterId, oldConnection, newConnection);
		updateBestWayDrain(transmitterId, oldConnection, newConnection);
	}

	private void updateBestWaySource(
			final long transmitterId, final TransmitterCommunicationInterface oldConnection, final TransmitterCommunicationInterface newConnection) {
		if(oldConnection == null || newConnection == null) return; 
		if(oldConnection == newConnection) return;
		RemoteSourceSubscription oldSub = null;
		RemoteSourceSubscription newSub = null;
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription instanceof RemoteSourceSubscription) {
				RemoteSourceSubscription subscription = (RemoteSourceSubscription)sendingSubscription;
				if(subscription.getCentralDistributorId() == transmitterId){
					if(subscription.getCommunication() == oldConnection){
						oldSub = subscription;
					}
					else if(subscription.getCommunication() == newConnection){
						newSub = subscription;
					}
				}
			}
		}
		if(oldSub == null) return;
		if(newSub == null) {
			newSub = new RemoteSourceSubscription(_subscriptionsManager, _baseSubscriptionInfo, newConnection);
			addReplacementSubscription(transmitterId, newSub);
			newSub.setState(SenderState.WAITING, -1);
			newSub.setPotentialDistributors(Arrays.asList(transmitterId));
			newSub.subscribe();
		}
		else if(!_subscriptionList.hasDrainOrSource()) {
			// Wenn schon eine neue Verbindung besteht, passiert das gew�hnlich wenn noch kein Zentraldatenverteiler gefunden wurde.
			// Hier einfach die (nicht erfolgreichen) Anmeldungen umbiegen.
			oldSub.removePotentialDistributor(transmitterId);
			oldSub.subscribe();
			newSub.addPotentialDistributor(transmitterId);
			newSub.subscribe();
		}
	}

	private void updateBestWayDrain(
			final long transmitterId, final TransmitterCommunicationInterface oldConnection, final TransmitterCommunicationInterface newConnection) {
		if(oldConnection == null || newConnection == null) return;  
		if(oldConnection == newConnection) return;
		RemoteDrainSubscription oldSub = null;
		RemoteDrainSubscription newSub = null;
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription instanceof RemoteDrainSubscription) {
				RemoteDrainSubscription subscription = (RemoteDrainSubscription)receivingSubscription;
				if(subscription.getCentralDistributorId() == transmitterId){
					if(subscription.getCommunication() == oldConnection){
						oldSub = subscription;
					}
					else if(subscription.getCommunication() == newConnection){
						newSub = subscription;
					}
				}
			}
		}
		if(oldSub == null) return;
		if(newSub == null) {
			newSub = new RemoteDrainSubscription(_subscriptionsManager, _baseSubscriptionInfo, newConnection);
			addReplacementSubscription(transmitterId, newSub);
			newSub.setPotentialDistributors(Arrays.asList(transmitterId));
			newSub.subscribe();
		}
		else {
			addReplacementSubscription(transmitterId, newSub);
			newSub.addPotentialDistributor(transmitterId);
			newSub.subscribe();
		}
	}

	private void addReplacementSubscription(final long transmitterId, final RemoteCentralSubscription newSub) {
		PendingSubscription old = _pendingSubscriptions.put(transmitterId, new PendingSubscription(newSub));
		if(old != null){
			// Alte Umleitung entfernen (wieder abmelden)
			old.getNewSubscription().unsubscribe();
		}
	}

	public synchronized byte[] serializeToBytes() throws IOException {
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
		try {
			Collection<SendingSubscription> sendingSubscriptions = _subscriptionList.getSendingSubscriptions();
			dataOutputStream.writeInt(sendingSubscriptions.size());
			for(final SendingSubscription sendingSubscription : sendingSubscriptions) {
				dataOutputStream.writeBoolean(sendingSubscription instanceof LocalSubscription);
				dataOutputStream.writeLong(sendingSubscription.getCommunication().getId());
				dataOutputStream.writeLong(sendingSubscription.getUserId());
				dataOutputStream.writeBoolean(sendingSubscription.isSource());
				dataOutputStream.writeBoolean(sendingSubscription.isRequestSupported());
				dataOutputStream.writeInt(sendingSubscription.getState().ordinal());
				dataOutputStream.writeInt(sendingSubscription.getConnectionState().ordinal());
			}
			Collection<ReceivingSubscription> receivingSubscriptions = _subscriptionList.getReceivingSubscriptions();
			dataOutputStream.writeInt(receivingSubscriptions.size());
			for(final ReceivingSubscription receivingSubscription : receivingSubscriptions) {
				dataOutputStream.writeBoolean(receivingSubscription instanceof LocalSubscription);
				dataOutputStream.writeLong(receivingSubscription.getCommunication().getId());
				dataOutputStream.writeLong(receivingSubscription.getUserId());
				dataOutputStream.writeBoolean(receivingSubscription.isDrain());
				dataOutputStream.writeBoolean(receivingSubscription.getReceiveOptions().withDelayed());
				dataOutputStream.writeBoolean(receivingSubscription.getReceiveOptions().withDelta());
				dataOutputStream.writeInt(receivingSubscription.getState().ordinal());
				dataOutputStream.writeInt(receivingSubscription.getConnectionState().ordinal());
			}
			List<Long> potentialCentralDistributors = _subscriptionsManager.getPotentialCentralDistributors(_baseSubscriptionInfo);
			dataOutputStream.writeInt(potentialCentralDistributors.size());
			for(Long potentialCentralDistributor : potentialCentralDistributors) {
				dataOutputStream.writeLong(potentialCentralDistributor);
				TransmitterCommunicationInterface connection = _subscriptionsManager.getBestConnectionToRemoteDav(
						potentialCentralDistributor
				);
				long id = connection.getId();
				int resistance = connection.getThroughputResistance();
				long remoteUserId = connection.getRemoteUserId();
				dataOutputStream.writeLong(id);
				dataOutputStream.writeInt(resistance);
				dataOutputStream.writeLong(remoteUserId);
			}
		}
		finally {
			dataOutputStream.close();
		}
		return byteArrayOutputStream.toByteArray();
	}

	@Override
	public String toString() {
		return _subscriptionsManager.subscriptionToString(_baseSubscriptionInfo);
	}

	public synchronized RemoteDrainSubscription getOrCreateRemoteDrainSubscription(final TransmitterCommunicationInterface connection) {
		List<ReceivingSubscription> subscriptions = getReceivingSubscriptions(connection);
		for(ReceivingSubscription subscription : subscriptions) {
			if(subscription instanceof RemoteDrainSubscription) {
				return (RemoteDrainSubscription)subscription;
			}
		}
		RemoteDrainSubscription subscription = new RemoteDrainSubscription(_subscriptionsManager, _baseSubscriptionInfo, connection);
		addReceivingSubscription(subscription);
		return subscription;
	}

	public synchronized RemoteSourceSubscription getOrCreateRemoteSourceSubscription(final TransmitterCommunicationInterface connection) {
		List<SendingSubscription> subscriptions = getSendingSubscriptions(connection);
		for(SendingSubscription subscription : subscriptions) {
			if(subscription instanceof RemoteSourceSubscription) {
				return (RemoteSourceSubscription)subscription;
			}
		}
		RemoteSourceSubscription subscription = new RemoteSourceSubscription(_subscriptionsManager, _baseSubscriptionInfo, connection);
		addSendingSubscription(subscription);
		return subscription;
	}

	public synchronized void updateOrCreateRemoteReceiverSubscription(
			final TransmitterCommunicationInterface communication, final Collection<Long> ids, final BaseSubscriptionInfo baseSubscriptionInfo) {
		for(ReceivingSubscription receivingSubscription : _subscriptionList.getReceivingSubscriptions()) {
			if(receivingSubscription.getCommunication() == communication && receivingSubscription instanceof RemoteReceiverSubscription){
				((RemoteReceiverSubscription)receivingSubscription).setPotentialDistributors(ids);

				// Status neu setzen, damit eine eventuelle Anmeldung beim anderen Datenverteiler aktualisiert wird
				receivingSubscription.setState(receivingSubscription.getState(), getCentralDistributorId());

				updateRemoteConnections();
				return;
			}
		}
		final RemoteSubscription remoteSubscription;
		remoteSubscription = new RemoteReceiverSubscription(_subscriptionsManager, communication, baseSubscriptionInfo, ids);
		addReceivingSubscription((ReceivingSubscription)remoteSubscription);
	}

	public synchronized void updateOrCreateRemoteSenderSubscription(
			final TransmitterCommunicationInterface communication, final Collection<Long> ids, final BaseSubscriptionInfo baseSubscriptionInfo) {
		for(SendingSubscription sendingSubscription : _subscriptionList.getSendingSubscriptions()) {
			if(sendingSubscription.getCommunication() == communication && sendingSubscription instanceof RemoteSenderSubscription){
				((RemoteSenderSubscription)sendingSubscription).setPotentialDistributors(ids);

				// Status neu setzen, damit eine eventuelle Anmeldung beim anderen Datenverteielr aktualisiert wird
				sendingSubscription.setState(sendingSubscription.getState(), getCentralDistributorId());

				updateRemoteConnections();
				return;
			}
		}
		final RemoteSubscription remoteSubscription;
		remoteSubscription = new RemoteSenderSubscription(_subscriptionsManager, communication, baseSubscriptionInfo, ids);
		addSendingSubscription((SendingSubscription)remoteSubscription);
	}

	/**
	 * Markiert das SubscriptionInfo als offen, sodass �nderungen an den Anmeldungen durchgef�hrt werden d�rfen.
	 *
	 * Wird auf den SubscriptionsManager synchronisiert ausgef�hrt
	 */
	public void open() {
		_referenceCounter++;
	}

	/**
	 * Markiert das SubscriptionInfo als geschlossen, nachdem �nderungen an den Anmeldungen durchgef�hrt wurden.
	 * Falls das Objekt leer ist und von keinem mehr offen ist, wird gepr�ft ob Anmeldungen vorhanden sind.
	 * Falls nicht, wird das Objekt aus dem SubscriptionsManager entfernt.
	 *
	 * Synchronisiert auf den _subscriptionsManager, daher keine Synchronisation von _referenceCounter notwendig.
	 */
	public void close() {
		synchronized(_subscriptionsManager) {
			_referenceCounter--;
			if(_referenceCounter == 0 && isEmpty()) {
				_subscriptionsManager.removeSubscriptionInfo(this);
			}
		}
	}

	private class PendingSubscription {
		private final RemoteCentralSubscription _newSubscription;
		private long _lastReceivedDataIndex = 1;

		private PendingSubscription(final RemoteCentralSubscription newSubscription) {
			_newSubscription = newSubscription;
		}

		private long getLastReceivedDataIndex() {
			return _lastReceivedDataIndex;
		}

		private void setLastReceivedDataIndex(final long lastReceivedDataIndex) {
			_lastReceivedDataIndex = lastReceivedDataIndex;
		}

		public RemoteCentralSubscription getNewSubscription() {
			return _newSubscription;
		}
	}
}
