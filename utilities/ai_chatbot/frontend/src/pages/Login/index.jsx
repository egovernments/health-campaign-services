import axios from 'axios';
import { useState, useEffect, useContext } from 'react';
import { useHistory } from 'react-router-dom';
import { Layout, Card, Form, Input, Button, notification } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';

import config from '../../config';
import AuthContext from '../../state/authContex';
import LoginLogo from '../../assets/imgs/prescience.png';
import coverImage from '../../assets/imgs/chatbot_cover.png';
import { getUISettings, getVersion } from '../../services/ProductApis';

const Login = () => {
  const [loading, setLoading] = useState(false);
  const [logoUrl, setLogoUrl] = useState(LoginLogo);
  const [logoBackground, setLogoBackground] = useState('white');
  const [submitButtonColor, setSubmitButtonColor] = useState('#3E1E6E');
  const [settingsLoaded, setSettingsLoaded] = useState(false);

  const history = useHistory();
  const authCntx = useContext(AuthContext);
  const { Content } = Layout;

  useEffect(() => {
    const fetchUISettings = async () => {
      try {
        const cachedUISettings = localStorage.getItem('uiSettings');
        if (cachedUISettings) {
          const settings = JSON.parse(cachedUISettings);
          setLogoUrl(settings.login_logo_url || LoginLogo);
          setLogoBackground(settings.login_logo_background_color || logoBackground);
          setSubmitButtonColor(settings.login_button_color || submitButtonColor);
        } else {
          const response = await getUISettings();
          const settings = JSON.parse(response.data.value);
          setLogoUrl(settings.login_logo_url || LoginLogo);
          setLogoBackground(settings.login_logo_background_color || logoBackground);
          setSubmitButtonColor(settings.login_button_color || submitButtonColor);
          localStorage.setItem('uiSettings', JSON.stringify(settings));
        }
      } catch (error) {
        console.error('Error fetching UI settings:', error);
      } finally {
        setSettingsLoaded(true);
      }
    };
    fetchUISettings();
  }, []); // Empty dependency array ensures the effect runs only once

  const handleLogin = async (values) => {
    setLoading(true);
    // console.log('Attempting login with values:', values);

    try {
      const response = await axios.post(`${config.backendHost}/api/v1/users/login`, values);
      const { access_token, roles, guid, user_name } = response.data;

      // console.log('Login API response:', response.data);

      const role = Array.isArray(roles) ? roles.map((role) => role.code).join(',') : '';

      authCntx.login(access_token, role, guid, user_name);

      const versionResponse = await getVersion();
      localStorage.setItem('appVersion', versionResponse.data.version);

      history.push('/');
    } catch (error) {
      console.error('Login failed:', error);
      notification.open({
        message: 'Invalid credentials.',
        description: error?.response?.data?.error || 'Login failed',
        className: 'custom-class',
        style: { width: 350, color: 'red' },
      });
    }

    setLoading(false);
  };

  if (!settingsLoaded) return null;

  return (
    <Layout style={{ minHeight: '100vh', alignItems: 'left', backgroundImage: `url(${coverImage})`, backgroundSize: 'cover' }}>
      <Content style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', height: '100vh', paddingRight: '10vw' }}>
        <Card style={{ width: 400, height: 'fit-content' }}>
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <img src={logoUrl} alt='logo' width='200px' style={{ backgroundColor: logoBackground }} />
          </div>
          <div style={{ marginTop: '40px' }}>
            <Form onFinish={handleLogin}>
              <Form.Item name='user_name' rules={[{ required: true, message: 'Please input your email/userid.' }]}>
                <Input style={{ height: '40px' }} prefix={<UserOutlined style={{ color: '#3E1E6E' }} />} placeholder='Email/UserId' />
              </Form.Item>
              <Form.Item name='password' rules={[{ required: true, message: 'Please input your password.' }]}>
                <Input.Password style={{ height: '40px' }} prefix={<LockOutlined style={{ color: '#3E1E6E' }} />} placeholder='Password' />
              </Form.Item>
              <Form.Item>
                <Button htmlType='submit' loading={loading} style={{ height: '40px', color: 'white', backgroundColor: submitButtonColor }} block>
                  Login
                </Button>
              </Form.Item>
            </Form>
          </div>
        </Card>
      </Content>
      <div style={{ position: 'absolute', bottom: '10px', left: '50%', transform: 'translateX(-50%)', color: 'white', fontSize: '16px' }}>
        © {new Date().getFullYear()} Prescience Decision Solutions. All rights reserved.
      </div>
    </Layout>
  );
};

export default Login;
