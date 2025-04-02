import { logger } from "../../utils/logger";

interface Application {
    id: string;
    name: string;
    status: string;
    createdAt: Date;
    Application: any;
    Workflow: any;
    updatedAt?: Date;
}

let applicationStore: Application[] = []; // In-memory storage for simplicity

class ApplicationService {
    /**
     * Creates a new application and stores it.
     * @param data The application data from the request body.
     * @returns The newly created application.
     */
    async createApplication(data: any): Promise<Application> {
        const newApplication: Application = {
            id: String(applicationStore.length + 1),
            name: data.name,
            status: data.status,
            Application:{...data?.Application,    id: String(applicationStore.length + 1),
            },
            Workflow:data?.Workflow,
            createdAt: new Date(),
        };

        applicationStore.push(newApplication);
        logger.info(`Application created: ${JSON.stringify(newApplication)}`);
        return newApplication;
    }

    /**
     * Retrieves applications based on search criteria.
     * @param filter Search parameters (optional).
     * @returns A list of matching applications.
     */
    async getApplications(filter?: { name?: string; status?: string }): Promise<Application[]> {
        logger.info(`Fetching applications with filter: ${JSON.stringify(filter)}`);
        if (!filter) return applicationStore;

        return applicationStore.filter(app =>
            (!filter.name || app.name.includes(filter.name)) &&
            (!filter.status || app.status === filter.status)
        );
    }

    /**
     * Updates an existing application by ID.
     * @param id The application ID.
     * @param data The fields to update.
     * @returns The updated application.
     */
    async updateApplication(id: string, data: { name?: string; status?: string }): Promise<Application | null> {
        const appIndex = applicationStore.findIndex(app => app.id === id);
        if (appIndex === -1) {
            logger.warn(`Application with ID ${id} not found.`);
            return null;
        }

        applicationStore[appIndex] = {
            ...applicationStore[appIndex],
            ...data,
            updatedAt: new Date(),
        };

        logger.info(`Application updated: ${JSON.stringify(applicationStore[appIndex])}`);
        return applicationStore[appIndex];
    }

    /**
     * Deletes an application by ID.
     * @param id The application ID.
     * @returns A success message.
     */
    async deleteApplication(id: string): Promise<{ message: string }> {
        const appIndex = applicationStore.findIndex(app => app.id === id);
        if (appIndex === -1) {
            logger.warn(`Application with ID ${id} not found.`);
            return { message: "Application not found" };
        }

        applicationStore.splice(appIndex, 1);
        logger.info(`Application with ID ${id} deleted.`);
        return { message: "Application deleted successfully" };
    }
}

export default new ApplicationService();
