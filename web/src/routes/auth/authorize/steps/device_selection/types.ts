import type {Snippet} from "svelte";
import type {NewDeviceNameResult} from "./DeviceSelectionPlugin.svelte.ts";

export type DeviceOption = {
    device_id: string;
    friendly_name: string;
    display_name: string;
    manufacturer: string;
    model: string;
    type: "Phone";
    created_at: number
};

export type DeviceSelectState = {
    options: DeviceOption[]
} | null;

export type DeviceSelectPluginInstance = {
    namespace: string;
    isActive: boolean;
    data: DeviceSelectState;
    selectDevice: (deviceId: string) => Promise<void>;
    newDevice: (name: string) => Promise<NewDeviceNameResult>;
};

export type DeviceSelectSnippet = Snippet<[DeviceSelectPluginInstance]>;
