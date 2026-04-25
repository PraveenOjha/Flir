import { useState, useEffect } from 'react';
import { NativeModules, NativeEventEmitter } from 'react-native';

const { FlirModule } = NativeModules as any;
const FlirEmitter = new NativeEventEmitter(FlirModule);

export function useFlirTemperature() {
  const [temperature, setTemperature] = useState<number | null>(null);

  useEffect(() => {
    const subscription = FlirEmitter.addListener('FlirTemperatureChanged', (temp: number) => {
      // Convert Kelvin to Celsius if raw thermal data is detected
      const normalizedTemp = (typeof temp === 'number' && temp > 200) ? temp - 273.15 : temp;
      setTemperature(normalizedTemp);
    });
    return () => subscription.remove();
  }, []);

  return temperature;
}
