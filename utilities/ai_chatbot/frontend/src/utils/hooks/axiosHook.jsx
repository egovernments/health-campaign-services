import  { useState, useEffect } from "react";

export function useAxiosHooks({
    apiMethod,
    callback,
    safeCallback,
    data = null
}) {
    const [response, setResponse] = useState(null);
    const [error, setError] = useState("");
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const fetchData = async () => {
            try {
                apiMethod(data)
                    .then((res) => {
                        if (callback) return callback(res);
                        else return res;
                    })
                    .then((res) => {
                        if (safeCallback) safeCallback(res);
                        setResponse(res);
                    })
                    .finally(() => {
                        setIsLoading(false);
                    });
            } catch (err) {
                setError(err);
            }
        };

        fetchData();

        // eslint-disable-next-line
    }, [apiMethod, data]);

    return { response, error, isLoading };
}
