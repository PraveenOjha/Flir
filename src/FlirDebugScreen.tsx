import React, { useEffect, useState } from 'react';
import { View, Text, Button, StyleSheet, NativeModules, NativeEventEmitter } from 'react-native';

const { FlirModule } = NativeModules as any;
const FlirEmitter = new NativeEventEmitter(FlirModule);

export function FlirDebugScreen() {
  const [battery, setBattery] = useState<number | null>(null);
  const [isCharging, setIsCharging] = useState<boolean | null>(null);
  const [temperature, setTemperature] = useState<number | null>(null);
  const [lastEvent, setLastEvent] = useState<any>(null);

  useEffect(() => {
    const subscription = FlirEmitter.addListener('FlirBatteryUpdated', (evt) => {
      setBattery(evt.level);
      setIsCharging(evt.isCharging);
      setLastEvent(evt);
    });
    return () => subscription.remove();
  }, []);

  const onSimulateContextLoss = async () => {
    try {
      if (FlirModule && FlirModule.simulateFlirContextLoss) {
        await FlirModule.simulateFlirContextLoss();
        console.log('[FLIR DEBUG] Simulated context loss');
      }
    } catch (e) {
      console.warn('[FLIR DEBUG] simulateContextLoss error', e);
    }
  };

  const onRequestBattery = async () => {
    try {
      if (FlirModule && FlirModule.getBatteryLevel) {
        const lvl = await FlirModule.getBatteryLevel();
        setBattery(typeof lvl === 'number' ? lvl : null);
      }
      try {
        if (FlirModule && FlirModule.isBatteryCharging) {
          const ch = await FlirModule.isBatteryCharging();
          setIsCharging(Boolean(ch));
        }
      } catch (e) { /* ignore */ }
    } catch (e) {
      console.warn('getBatteryLevel error', e);
    }
  };

  const onRequestTemperature = async () => {
    try {
      if (FlirModule && FlirModule.getTemperatureAt) {
        const val = await FlirModule.getTemperatureAt(80, 60);
        setTemperature(typeof val === 'number' ? val : null);
      }
    } catch (e) {
      console.warn('getTemperatureAt error', e);
    }
  };

  const onPauseFlir = async () => {
    try {
      if (FlirModule && FlirModule.pauseFlirForPreview) {
        await FlirModule.pauseFlirForPreview();
      }
    } catch (e) {
      console.warn('[FLIR DEBUG] pauseFlirForPreview error', e);
    }
  };

  const onResumeFlir = async () => {
    try {
      if (FlirModule && FlirModule.resumeFlirAfterPreview) {
        await FlirModule.resumeFlirAfterPreview();
      }
    } catch (e) {
      console.warn('[FLIR DEBUG] resumeFlirAfterPreview error', e);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>FLIR Debug (Package)</Text>
      <Text>Battery Level: {battery ?? '--'}</Text>
      <Text>Charging: {isCharging == null ? '--' : isCharging ? 'Yes' : 'No'}</Text>
      <Text>Temperature at (80,60): {temperature == null ? '--' : temperature.toFixed(1) + '°C'}</Text>
      <Text>Last Event: {lastEvent ? JSON.stringify(lastEvent) : '--'}</Text>
      <View style={styles.row}>
        <Button title="Simulate Context Loss" onPress={onSimulateContextLoss} />
        <Button title="Request Battery" onPress={onRequestBattery} />
        <Button title="Get Temp (80,60)" onPress={onRequestTemperature} />
      </View>
      <View style={[styles.row, { marginTop: 8 }] }>
        <Button title="Pause FLIR (Preview Pause)" onPress={onPauseFlir} />
        <Button title="Resume FLIR (Preview Resume)" onPress={onResumeFlir} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 12 },
  title: { fontSize: 18, fontWeight: 'bold', marginBottom: 8 },
  row: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 12 }
});
