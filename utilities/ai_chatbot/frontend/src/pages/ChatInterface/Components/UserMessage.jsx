import '../styles.scss';
import jwt_decode from 'jwt-decode';
import React, { useState } from 'react';
import { addToFavorites } from '../../../services/ProductApis';
import { CloseCircleOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { Input, Button, Row, Col, Avatar, Space, Typography, Modal, Form, notification } from 'antd';

const { Text } = Typography;

const UserMessage = ({ message, setFavoritesReload }) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const [form] = Form.useForm();
  const { TextArea } = Input;
  const token = localStorage.getItem('chat_token');

  const notificationProps = { className: 'custom-class', style: { width: 350 } };
  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  const handleValuesChange = (changedValues, allValues) => {
    setIsButtonDisabled(!allValues.name || !allValues.description);
  };

  let guid = localStorage.getItem('guid');
  const handleStarClick = () => {
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleCancel = () => {
    setIsModalVisible(false);
    setIsButtonDisabled(true);
    form.resetFields();
  };

  const handleSubmit = async (values) => {
    try {
      const data = { name: values.name, description: values.description, type: 'Query' };
      await addToFavorites(data, guid);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Added ${values.name} favorite.`);
      setIsModalVisible(false);
      setFavoritesReload((prev) => !prev);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
      console.error('Error sending data to backend', error?.response?.data?.error);
    } finally {
      setName('');
      setDescription('');
    }
  };

  return (
    <div>
      <Row style={{ margin: '25px 0px' }} justify='end'>
        <Col>
          <Space direction='horizontal' align='end'>
            <div
              style={{
                background: '#e6f7ff',
                color: '#000',
                padding: '10px 15px',
                borderRadius: '10px',
                maxWidth: '100%',
                wordBreak: 'break-word',
                whiteSpace: 'pre-wrap',
                boxShadow: '0 2px 4px rgba(0, 0, 0, 0.1)',
                position: 'relative',
              }}>
              <Text>{message.text}</Text>
              <Button type='link' style={{ position: 'absolute', top: '-15px', right: '-6px', padding: 0 }} onClick={handleStarClick}>
                ⭐
              </Button>
            </div>
            <Avatar src='https://api.dicebear.com/7.x/miniavs/svg?seed=1' style={{ marginBottom: '5px' }} />
          </Space>
        </Col>
      </Row>

      {/* Modal for adding favorite */}
      <Modal title='Add Favorite' visible={isModalVisible} onCancel={handleCancel} footer={null} bodyStyle={{ padding: '0px 10px', paddingBottom: '1px' }} width={450}>
        <Form form={form} onFinish={handleSubmit} layout='vertical' onValuesChange={handleValuesChange}>
          <Form.Item
            label={
              <span>
                Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            style={{ marginBottom: 5 }}
            rules={[{ message: 'Please input the name.' }]}>
            <Input placeholder='Enter name' />
          </Form.Item>
          <Form.Item
            label={
              <span>
                Description <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='description'
            initialValue={message.text}
            style={{ marginBottom: 5 }}
            rules={[{ message: 'Please input the question.' }]}>
            <TextArea placeholder='Enter question' rows={4} style={{ resize: 'none' }} />
          </Form.Item>
          <Form.Item>
            <Button type='primary' htmlType='submit' block disabled={isButtonDisabled} style={{ width: '20%', float: 'right', marginBottom: -20 }}>
              Add
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserMessage;
