import { allProcesses } from "./constants";

export interface ResourceTypeConfig {
  type: string;
  processName: string;
  phase: number;
  dependsOn: string[];
  kafkaKey: string;
  isRequired: boolean;
  parentType: string | null;
  allowMultiplePerParent: boolean;
}

export const RESOURCE_TYPE_REGISTRY: Record<string, ResourceTypeConfig> = {
  facility: {
    type: "facility",
    processName: allProcesses.facilityCreation,
    phase: 1,
    dependsOn: [],
    kafkaKey: "facilityCreation_6f1b3a0e-d9a3-4c7f-947e-1fb82e36a10c",
    isRequired: true,
    parentType: null,
    allowMultiplePerParent: false
  },
  user: {
    type: "user",
    processName: allProcesses.userCreation,
    phase: 1,
    dependsOn: [],
    kafkaKey: "userCreation_d50a7c12-4569-4e63-b94f-e6219f0e8ba4",
    isRequired: true,
    parentType: null,
    allowMultiplePerParent: false
  },
  boundary: {
    type: "boundary",
    processName: allProcesses.projectCreation,
    phase: 1,
    dependsOn: [],
    kafkaKey: "projectCreation_a9b4826e-2ed4-4b94-9f7f-d1e921ab5a3d",
    isRequired: true,
    parentType: null,
    allowMultiplePerParent: false
  },
  attendanceRegister: {
    type: "attendanceRegister",
    processName: allProcesses.attendanceRegisterCreation,
    phase: 2,
    dependsOn: [allProcesses.projectCreation],
    kafkaKey: "attendanceRegisterCreation_c7e2f8a1-3b5d-4e9a-a1c6-9d4e7f2b8c3a",
    isRequired: false,
    parentType: null,
    allowMultiplePerParent: false
  },
  attendanceRegisterAttendee: {
    type: "attendanceRegisterAttendee",
    processName: allProcesses.attendanceRegisterAttendeeCreation,
    phase: 3,
    dependsOn: [allProcesses.attendanceRegisterCreation],
    kafkaKey: "attendanceRegisterAttendeeCreation_b8f3c1d2-4e5a-6f7b-8c9d-0e1f2a3b4c5d",
    isRequired: false,
    parentType: "attendanceRegister",
    allowMultiplePerParent: true
  }
};

const processNameToTypeMap: Map<string, string> = new Map(
  Object.values(RESOURCE_TYPE_REGISTRY).map(cfg => [cfg.processName, cfg.type])
);

export function getResourceTypeByProcessName(processName: string): string {
  return processNameToTypeMap.get(processName) || "";
}

export function getAllRequiredTypes(): string[] {
  return Object.values(RESOURCE_TYPE_REGISTRY)
    .filter(cfg => cfg.isRequired)
    .map(cfg => cfg.type);
}

export function getOptionalTypes(): string[] {
  return Object.values(RESOURCE_TYPE_REGISTRY)
    .filter(cfg => !cfg.isRequired)
    .map(cfg => cfg.type);
}

export function getAllAllowedTypes(): string[] {
  return Object.keys(RESOURCE_TYPE_REGISTRY);
}

export function getResourceConfigsByPhase(phase: number, filterTypes?: Set<string>): ResourceTypeConfig[] {
  return Object.values(RESOURCE_TYPE_REGISTRY)
    .filter(cfg => cfg.phase === phase && (!filterTypes || filterTypes.has(cfg.type)));
}

export function getProcessNamesForResourceTypes(types: string[]): string[] {
  return types
    .map(t => RESOURCE_TYPE_REGISTRY[t]?.processName)
    .filter(Boolean);
}

export function getRegistryEntry(type: string): ResourceTypeConfig | undefined {
  return RESOURCE_TYPE_REGISTRY[type];
}

export function getPhase2Types(): ResourceTypeConfig[] {
  return Object.values(RESOURCE_TYPE_REGISTRY).filter(cfg => cfg.phase === 2);
}

export function hasDependenciesMet(type: string, completedProcessNames: Set<string>): boolean {
  const config = RESOURCE_TYPE_REGISTRY[type];
  if (!config) return false;
  return config.dependsOn.every(dep => completedProcessNames.has(dep));
}

/**
 * Returns config if type is registered, otherwise returns a default "unregistered" config.
 * Unregistered types can be stored but will NOT participate in automated processing pipelines.
 */
export function getResourceConfigOrDefault(type: string): ResourceTypeConfig {
  if (RESOURCE_TYPE_REGISTRY[type]) {
    return RESOURCE_TYPE_REGISTRY[type];
  }
  return {
    type,
    processName: "",
    phase: 0,
    dependsOn: [],
    kafkaKey: "",
    isRequired: false,
    parentType: null,
    allowMultiplePerParent: false
  };
}

export function isRegisteredType(type: string): boolean {
  return type in RESOURCE_TYPE_REGISTRY;
}
