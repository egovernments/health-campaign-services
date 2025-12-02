import { lazy } from 'react';
import Settings from '../../pages/Settings';
import ChatHistory from '../../pages/ChatHistory';
const Tags = lazy(() => import('../../pages/Tags'));
const ProductInfo = lazy(() => import('../../pages/ProductInfo'));
const UserManagement = lazy(() => import('../../pages/UserManagement'));
const PageNotFound = lazy(() => import('../../pages/PageNotFound'));
const ModelManagement = lazy(() => import('../../pages/ModelManagement'));
const ChatInterface = lazy(() => import('../../pages/ChatInterface'));
const Connections = lazy(() => import('../../pages/Connections'));

const SuperAdminRoutes = {
  pages: [
    {
      name: 'ProductInfo',
      page: ProductInfo,
      path: ['/botconfiguration'],
      props: { exact: true },
    },
    {
      name: 'UserManagement',
      page: UserManagement,
      path: ['/usermanagement'],
      props: { exact: true },
    },
    {
      name: 'Connections',
      page: Connections,
      path: ['/connections'],
      props: { exact: true },
    },
    {
      name: 'ChatInterface',
      page: ChatInterface,
      path: ['/chat'],
      props: { exact: true },
    },
    {
      name: 'Settings',
      page: Settings,
      path: ['/settings'],
      props: { exact: true },
    },
    {
      name: 'Tags',
      page: Tags,
      path: ['/'],
      props: { exact: true },
    },
    {
      name: 'ChatHistory',
      page: ChatHistory,
      path: ['/chathistory'],
      props: { exact: true },
    },
    {
      name: 'ModelManagement',
      page: ModelManagement,
      path: ['/llmconfiguration'],
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

export default SuperAdminRoutes;
