/** Awaitable delay, shared so the sync consumers, the on-read refresh and the user batch loop agree. */
export function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
