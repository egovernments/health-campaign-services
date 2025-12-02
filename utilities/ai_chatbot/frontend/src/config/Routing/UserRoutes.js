import { lazy } from 'react';
import RedirectWrapper from '../../components/RedirectWrapper';
const ProductInfoUser = lazy(() => import('../../pages/ProductInfoUser'));
const PageNotFound = lazy(() => import('../../pages/PageNotFound'));
const ChatInterface = lazy(() => import('../../pages/ChatInterface'));

const UserRoutes = {
  pages: [
    {
      name: 'ProductInfoUser',
      page: () => <RedirectWrapper Component={ProductInfoUser} />,
      path: ['/'],
      props: { exact: true },
    },
    {
      name: 'ChatInterface',
      page: ChatInterface,
      path: ['/chat'],
      props: { exact: true },
    },
    {
      name: 'PageNotFound',
      page: PageNotFound,
      path: ['*'],
      props: { exact: true },
    },
  ],
};

export default UserRoutes;
