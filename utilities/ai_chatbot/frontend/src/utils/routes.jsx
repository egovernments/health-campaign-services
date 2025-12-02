import { useLocation, useHistory } from "react-router-dom";

export function useChangeRoute() {
    const { search } = useLocation();
    const history = useHistory();

    function changeRoutes(to) {
        history.push(to + search);
    }
    return { changeRoutes };
}
