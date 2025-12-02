import React, { useContext, lazy, Suspense } from 'react';
import { Route, Router, Switch } from 'react-router-dom';
import 'antd/dist/antd.min.css';
import { Layout } from 'antd';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import Footer from './components/Footer';
import LoadingWidget from '../../components/LoadingWidget';
import { History } from '../../utils/history';
import AuthContext from '../../state/authContex';
import SuperAdminRoutes from '../../config/Routing/SuperAdminRoutes';
import UserRoutes from '../../config/Routing/UserRoutes';
const PageNotFound = lazy(() => import('../../pages/PageNotFound'));
const Login = lazy(() => import('../../pages/Login'));

const { Content } = Layout;

const LazyLoadPage = ({ children }) => <Suspense fallback={<LoadingWidget />}>{children}</Suspense>;

const LayoutView = () => {
  const { isLoggedIn, roles } = useContext(AuthContext);

  const getRoutes = () => {
    if (roles === 'CHATBOT_ADMIN') return SuperAdminRoutes;
    return UserRoutes;
  };

  const renderLayout = (children, customStyle = {}) => (
    <Layout>
      <Navbar />
      <Layout>
        <Sidebar />
        <Content
          style={{
            marginLeft: '50px',
            marginRight: '2px',
            minHeight: '85vh',
            minWidth: '70vw',
            ...customStyle,
          }}>
          {children}
        </Content>
      </Layout>
      <Footer />
    </Layout>
  );

  const renderChatInterface = (children, customStyle = {}) => (
    <Layout>
      <Navbar />
      <Layout>
        <Content style={{ ...customStyle }}>{children}</Content>
      </Layout>
    </Layout>
  );

  const renderRoutes = () => {
    const routes = getRoutes();
    return routes.pages.map(({ name, page: Page, path, props }) => (
      <Route {...props} key={name} path={path}>
        {name === 'ChatInterface'
          ? renderChatInterface(
              <LazyLoadPage>
                <Page />
              </LazyLoadPage>
            )
          : renderLayout(
              <LazyLoadPage>
                <Page />
              </LazyLoadPage>
            )}
      </Route>
    ));
  };

  return (
    <Router history={History}>
      <Switch>
        {isLoggedIn ? (
          renderRoutes()
        ) : (
          <Route>
            <LazyLoadPage>
              <Login />
            </LazyLoadPage>
          </Route>
        )}
        <Route>
          {renderLayout(
            <LazyLoadPage>
              <PageNotFound />
            </LazyLoadPage>,
            { minWidth: '90vw' }
          )}
        </Route>
      </Switch>
    </Router>
  );
};

export default LayoutView;
