/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.binding.bluetooth.discovery.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.smarthome.binding.bluetooth.BluetoothAdapter;
import org.eclipse.smarthome.binding.bluetooth.BluetoothBindingConstants;
import org.eclipse.smarthome.binding.bluetooth.BluetoothDevice;
import org.eclipse.smarthome.binding.bluetooth.BluetoothDiscoveryListener;
import org.eclipse.smarthome.binding.bluetooth.discovery.BluetoothDiscoveryParticipant;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.UID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BluetoothDiscoveryService} handles searching for BLE devices.
 *
 * @author Chris Jackson - Initial Contribution
 * @author Kai Kreuzer - Introduced BluetoothAdapters and BluetoothDiscoveryParticipants
 *
 */
@Component(immediate = true, service = DiscoveryService.class, configurationPid = "discovery.bluetooth")
public class BluetoothDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(BluetoothDiscoveryService.class);

    private final static int SEARCH_TIME = 15;

    private final Set<BluetoothAdapter> adapters = new CopyOnWriteArraySet<>();
    private final Set<BluetoothDiscoveryParticipant> participants = new CopyOnWriteArraySet<>();
    private final Map<UID, BluetoothDiscoveryListener> registeredListeners = new HashMap<>();

    public BluetoothDiscoveryService() {
        super(SEARCH_TIME);
    }

    @Override
    protected void activate(Map<String, Object> configProperties) {
        logger.debug("Activating Bluetooth discovery service");
        super.activate(configProperties);
        startScan();
    }

    @Override
    @Modified
    protected void modified(Map<String, Object> configProperties) {
        super.modified(configProperties);
    }

    @Override
    public void deactivate() {
        logger.debug("Deactivating Bluetooth discovery service");
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addBluetoothAdapter(BluetoothAdapter adapter) {
        this.adapters.add(adapter);
        BluetoothDiscoveryListener listener = new BluetoothDiscoveryListener() {

            @Override
            public void deviceDiscovered(BluetoothDevice device) {
                BluetoothDiscoveryService.this.deviceDiscovered(adapter, device);

            }
        };
        adapter.addDiscoveryListener(listener);
        registeredListeners.put(adapter.getUID(), listener);
    }

    protected void removeBluetoothAdapter(BluetoothAdapter adapter) {
        this.adapters.remove(adapter);
        adapter.removeDiscoveryListener(registeredListeners.remove(adapter.getUID()));
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected void addBluetoothDiscoveryParticipant(BluetoothDiscoveryParticipant participant) {
        this.participants.add(participant);
    }

    protected void removeBluetoothDiscoveryParticipant(BluetoothDiscoveryParticipant participant) {
        this.participants.remove(participant);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        Set<ThingTypeUID> supportedThingTypes = new HashSet<>();
        supportedThingTypes.add(BluetoothBindingConstants.THING_TYPE_GENERIC);
        for (BluetoothDiscoveryParticipant participant : participants) {
            supportedThingTypes.addAll(participant.getSupportedThingTypeUIDs());
        }
        return supportedThingTypes;
    }

    @Override
    public void startScan() {
        for (BluetoothAdapter adapter : adapters) {
            adapter.scanStart();
        }
    }

    @Override
    public void stopScan() {
        for (BluetoothAdapter adapter : adapters) {
            adapter.scanStop();
        }
    }

    private void deviceDiscovered(BluetoothAdapter adapter, BluetoothDevice device) {
        for (BluetoothDiscoveryParticipant participant : participants) {
            try {
                DiscoveryResult result = participant.createResult(device);
                if (result != null) {
                    thingDiscovered(result);
                    return;
                }
            } catch (Exception e) {
                logger.error("Participant '{}' threw an exception", participant.getClass().getName(), e);
            }
        }

        // We did not find a thing type for this device, so let's treat it as a generic one
        String label = device.getName();
        if (label == null || label.length() == 0) {
            label = "Bluetooth Device " + device.getAddress().toString();
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(BluetoothBindingConstants.CONFIGURATION_ADDRESS, device.getAddress().toString());
        properties.put(BluetoothBindingConstants.PROPERTY_TXPOWER, Integer.toString(device.getTxPower()));
        if (device.getManufacturerName() != null) {
            properties.put(Thing.PROPERTY_VENDOR, device.getManufacturerName());
        }

        ThingUID thingUID = new ThingUID(BluetoothBindingConstants.THING_TYPE_GENERIC, adapter.getUID(),
                device.getAddress().toString().toLowerCase().replace(":", ""));

        // Create the discovery result and add to the inbox
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withBridge(adapter.getUID()).withLabel(label).build();
        thingDiscovered(discoveryResult);
    }
}
