import React, { useState, useEffect } from 'react';
import { SettingOutlined, CheckCircleOutlined, InfoCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Form, Input, Button, Layout, Card, notification, message, Tooltip } from 'antd';
import './style.scss';
import LoadingWidget from '../../components/LoadingWidget';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';
import { getPlatformConfig, updatePlatformCofigByName } from '../../services/ProductApis';

const { Header } = Layout;

function Settings() {
  const [form] = Form.useForm();
  const [tableData, setTableData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [formValues, setFormValues] = useState({});

  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };

  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  const getPlatformData = () => {
    setLoading(true);
    getPlatformConfig()
      .then((response) => {
        const { data } = response;
        if (data.length > 0) {
          const formattedData = data.map((item) => ({
            display_name: item.display_name,
            value: item.value,
            type: item.type,
            name: item.name,
            help: item.help_msg,
            readonly: item.readonly,
          }));
          setTableData(formattedData);
          setFormValues(Object.fromEntries(formattedData.map((item) => [item.display_name, item.value])));
        } else {
          setTableData([]);
        }
        setLoading(false);
      })
      .catch((error) => {
        console.error('Error fetching platform config:', error?.response?.data?.error);
        showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch history');
        setLoading(false);
      });
  };

  useEffect(() => {
    getPlatformData();
  }, []);

  const prettifyJSON = (jsonString) => {
    try {
      const jsonObject = JSON.parse(jsonString);
      return JSON.stringify(jsonObject, null, 2);
    } catch (error) {
      console.error('Error prettifying JSON:', error);
      return jsonString;
    }
  };

  const handleSave = async (item) => {
    try {
      const values = await form.validateFields([item.display_name]);
      const newValue = values[item.display_name];
      if (!newValue) {
        message.error('This field cannot be empty.');
        return;
      }
      const updatedValue = item.type === 'Json' ? JSON.parse(newValue) : newValue;
      const updatedData = {
        display_name: item.display_name,
        value: updatedValue,
        name: item.name,
        type: item.type,
      };
      await updatePlatformCofigByName(item.name, updatedData);
      notification.open({
        icon: <CheckCircleOutlined style={{ color: 'green' }} />,
        message: 'Success',
        description: 'Setting(s) updated successfully.',
        className: 'custom-class',
        style: { width: 350 },
      });
    } catch (error) {
      if (error?.name === 'ValidationError') {
        message.error('This field is required.');
      }
      notification.open({
        icon: <CloseCircleOutlined style={{ color: 'red' }} />,
        message: 'Error',
        description: error?.response?.data?.error || 'An error occurred while saving.',
        className: 'custom-class',
        style: { width: 350 },
      });
    }
  };

  return (
    <>
      <PageBreadcrumb title='Settings' />
      <Header className='sticky-header'>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
          }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <SettingOutlined style={{ fontSize: '20px', color: 'black' }} />
            <span
              style={{
                fontSize: '20px',
                color: 'black',
                marginLeft: '10px',
                fontWeight: 'bold',
              }}>
              Settings
            </span>
          </div>
        </div>
      </Header>
      {loading ? (
        <LoadingWidget />
      ) : (
        <main className='main-container'>
          <Card bordered={false}>
            <Form
              form={form}
              name='basic'
              labelCol={{ span: 8 }}
              wrapperCol={{ span: 23 }}
              autoComplete='off'
              onValuesChange={(changedValues, allValues) => {
                setFormValues(allValues);
              }}>
              {tableData.map((item, index) => (
                <div
                  key={index}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    marginBottom: -20,
                  }}>
                  <label style={{ width: '40%', fontSize: '16px' }}>
                    {item.display_name}:{' '}
                    <Tooltip title={item.help}>
                      <InfoCircleOutlined size='small' />
                    </Tooltip>
                  </label>
                  <div style={{ width: '60%', marginTop: 23 }}>
                    <Form.Item name={item.display_name} initialValue={item.type === 'Json' ? prettifyJSON(item.value) : item.value} rules={[{ required: true, message: 'This field is required.' }]}>
                      {item.type === 'Json' ? <Input.TextArea rows={8} className='no-resize' disabled={item.readonly} /> : <Input disabled={item.readonly} />}
                    </Form.Item>
                  </div>
                  <Button type='primary' style={{ color: 'white', marginTop: '-1.5px' }} onClick={() => handleSave(item)} disabled={item.readonly || !formValues[item.display_name]?.trim()}>
                    Save
                  </Button>
                </div>
              ))}
            </Form>
          </Card>
        </main>
      )}
    </>
  );
}

export default Settings;
