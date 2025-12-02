import dayjs from 'dayjs';
import React, { useState, useEffect } from 'react';
import LoadingWidget from '../../components/LoadingWidget';
import ResizableReact from '../../components/ResizeableReact';
import { validateFourChar } from '../../utils/validation/validateFourChar';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';
import { createNewConnection, editConnection, getAllConnections, deleteConnection, getConnectionById, testConnection, getSupportedConnections } from '../../services/ProductApis';
import { Button, Form, Input, Table, Modal, Spin, notification, Layout, Dropdown, Menu, Tag, Card, Select, Switch } from 'antd';
import { DatabaseOutlined, EllipsisOutlined, EditFilled, DeleteFilled, CloseCircleOutlined, CheckCircleOutlined, LoadingOutlined, ApiFilled } from '@ant-design/icons';

const { Header } = Layout;
const { Option } = Select;

const Connections = () => {
  const [connections, setConnections] = useState([]);
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [currentConnection, setCurrentConnection] = useState(null);
  const [supportedConnection, setSupportedConnection] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [isEditButtonDisabled, setEditIsButtonDisabled] = useState(false);
  const [pageSize, setpageSize] = useState(10);
  const [selectedDbType, setSelectedDbType] = useState('');
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteId, setDeleteId] = useState(null);
  const [deleteName, setDeleteName] = useState('');

  const [columns, setColumns] = useState([
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      width: 50,
    },
    {
      title: <div style={{ textAlign: 'center' }}>Name</div>,
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      width: 150,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: <div style={{ textAlign: 'center' }}>Type</div>,
      key: 'type',
      align: 'center',
      ellipsis: true,
      width: 150,
      render: (text, record) => {
        let dbType = '';
        try {
          const parsedData = typeof record.data === 'string' ? JSON.parse(record.data) : record.data;
          dbType = parsedData?.db_type || '';
        } catch (error) {
          dbType = '';
        }
        return <div>{dbType}</div>;
      },
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Test Status',
      dataIndex: 'test_status',
      align: 'center',
      key: 'test_status',
      render: (test_status) => {
        if (test_status === 'loading') {
          return <Spin indicator={<LoadingOutlined spin />} size='medium' />;
        }
        const isSuccess = test_status?.toLowerCase() === 'success' || test_status === 'true';

        return <Tag color={isSuccess ? 'green' : 'volcano'}>{isSuccess ? 'Success' : 'Failure'}</Tag>;
      },
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      width: 120,
    },
    {
      title: 'Created At',
      dataIndex: 'created_at',
      align: 'center',
      key: 'created_at',
      render: (text) => (dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : ''),
      width: 150,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Tested At',
      dataIndex: 'tested_at',
      align: 'center',
      key: 'tested_at',
      render: (text) => (dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : ''),
      width: 150,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Updated At',
      dataIndex: 'updated_at',
      align: 'center',
      key: 'updated_at',
      render: (text) => (dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : ''),
      width: 150,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Actions',
      align: 'center',
      key: 'actions',
      render: (text, record) => (
        <Dropdown overlay={getActionMenu(record)} trigger={['click']}>
          <Button type='text' icon={<EllipsisOutlined rotate={90} />} />
        </Dropdown>
      ),
      width: 80,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
  ]);

  const [form] = Form.useForm();

  useEffect(() => {
    fetchConnections();
  }, []);

  useEffect(() => {
    if (currentConnection) {
      form.setFieldsValue({
        name: currentConnection.name,
        type: currentConnection.type,
        url: currentConnection.url,
        credentials: currentConnection.credentials,
      });
    }
  }, [currentConnection, form]);

  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };

  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  const fetchConnections = async () => {
    setLoading(true);
    try {
      const response = await getAllConnections();
      if (response) {
        setConnections(response.data);
      }
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response ? error?.response?.data?.error : error?.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (values) => {
    setLoading(true);
    setIsCreateModalVisible(false);
    setIsEditModalVisible(false);
    let connectionData;

    try {
      if (values.type === 'Databricks') {
        connectionData = {
          name: values.name,
          description: '',
          is_active: true,
          type: values.type,
          data: {
            db_type: 'Databricks',
            host: values.host,
            http_path: values.http_path,
            access_token: values.access_token,
            catalog: values.catalog,
          },
        };
      } else if (values.type === 'Elasticsearch') {
        connectionData = {
          name: values.name,
          description: '',
          is_active: true,
          type: values.type,
          data: {
            host: values.host,
            port: values.port,
            user_name: values.user_name,
            password: values.password,
            is_secure: values.is_secure || false,
            auth_type: values.auth_type || 'http_auth',
          },
        };
      } else {
        connectionData = {
          name: values.name,
          description: '',
          is_active: true,
          type: values.type,
          data: {
            host: values.host,
            port: values.port,
            user_name: values.user_name,
            password: values.password,
            db_name: values.db_name,
            is_secure: values.is_secure || false,
          },
        };
      }

      if (currentConnection) {
        await editConnection(currentConnection.id, connectionData);
        showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Connection ${currentConnection.id} updated.`);
      } else {
        await createNewConnection(connectionData);
        showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Connection created.');
      }

      setCurrentConnection(null);
      fetchConnections();
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || error?.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (currentConnection) {
      let parsedData = {};
      try {
        parsedData = typeof currentConnection.data === 'string' ? JSON.parse(currentConnection.data) : currentConnection.data;
      } catch (error) {
        console.error('Error parsing connection data:', error);
      }

      // Detect DB type
      const dbType = parsedData.db_type || currentConnection.type || '';

      // Update selected DB type (for showing proper form)
      setSelectedDbType(dbType);

      // Populate fields depending on type
      if (dbType === 'Databricks') {
        form.setFieldsValue({
          name: currentConnection.name,
          type: dbType,
          host: parsedData.host || '',
          http_path: parsedData.http_path || '',
          access_token: parsedData.access_token || '',
          catalog: parsedData.catalog || '',
        });
      } else if (dbType === 'Elasticsearch') {
        form.setFieldsValue({
          name: currentConnection.name,
          type: dbType,
          is_secure: parsedData.is_secure || false,
          host: parsedData.host || '',
          port: parsedData.port || '',
          user_name: parsedData.user_name || '',
          password: parsedData.password || '',
          auth_type: parsedData.auth_type
        });
      } else {
        form.setFieldsValue({
          name: currentConnection.name,
          type: dbType,
          is_secure: parsedData.is_secure || false,
          host: parsedData.host || '',
          port: parsedData.port || '',
          db_name: parsedData.db_name || '',
          user_name: parsedData.user_name || '',
          password: parsedData.password || '',
        });
      }
    }
  }, [currentConnection, form]);

  useEffect(() => {
    const type = form.getFieldValue('type');
    if (type) {
      setSelectedDbType(type);
    }
  }, [isCreateModalVisible, isEditModalVisible]);

  const fetchSupportedConnections = async () => {
    try {
      const res = await getSupportedConnections();
      setSupportedConnection(Array.isArray(res?.data) ? res.data : []);
    } catch (err) {
      console.error('Error fetching supported connections:', err);
      setSupportedConnection([]);
    }
  };

  const handleValuesChange = (changedValues, allValues) => {
    const { name, host, port, db_name, user_name, password, type, http_path, access_token, catalog, auth_type } = allValues;

    let allRequiredFieldsFilled = false;

    if (type === 'Databricks') {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!http_path && !!access_token && !!catalog;
    } else if (type === 'Elasticsearch') {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!user_name && !!password && !!auth_type;
    } else {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!port && !!db_name && !!user_name && !!password;
    }

    setIsButtonDisabled(!allRequiredFieldsFilled);
  };

  const handleEditValuesChange = (changedValues, allValues) => {
    const { name, host, port, db_name, user_name, password, type, http_path, access_token, catalog, auth_type } = allValues;

    let allRequiredFieldsFilled = false;

    if (type === 'Databricks') {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!http_path && !!access_token && !!catalog;
    } else if (type === 'Elasticsearch') {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!user_name && !!password && !!auth_type;
    } else {
      allRequiredFieldsFilled = !!name && !!type && !!host && !!port && !!db_name && !!user_name && !!password;
    }

    setEditIsButtonDisabled(!allRequiredFieldsFilled);
  };

  const showCreateModal = async () => {
    setIsButtonDisabled(true);
    setIsCreateModalVisible(true);
    setSelectedDbType('');
    form.resetFields();

    try {
      const res = await getSupportedConnections();
      const supported = Array.isArray(res?.data) ? res.data : [];
      setSupportedConnection(supported);

      if (supported.length > 0) {
        const defaultType = supported[0].name;
        let defaultPort = '';

        switch (defaultType) {
          case 'PostgreSQL':
            defaultPort = '5432';
            break;
          case 'MSSQL':
            defaultPort = '1433';
            break;
          case 'MySQL':
            defaultPort = '3306';
            break;
          case 'Redshift':
            defaultPort = '5439';
            break;
          default:
            defaultPort = '';
        }

        form.setFieldsValue({
          type: defaultType,
          port: defaultPort,
        });
        setSelectedDbType(defaultType);
      }
    } catch (err) {
      console.error('Error fetching supported connections:', err);
      setSupportedConnection([]);
    }
  };

  const showEditModal = async (record) => {
    setIsEditModalVisible(true);
    fetchSupportedConnections();
    try {
      const response = await getConnectionById(record.id);
      setCurrentConnection(response.data);

      // Parse the connection data to get the database type
      let parsedData = {};
      try {
        parsedData = typeof response.data.data === 'string' ? JSON.parse(response.data.data) : response.data.data;
      } catch (error) {
        console.error('Error parsing connection data:', error);
      }

      // Set the selected database type based on the connection data
      setSelectedDbType(parsedData.db_type);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.message);
    }
  };

  const handleCreateCancel = () => {
    setIsCreateModalVisible(false);
    setSelectedDbType(''); // reset
    form.resetFields(); // reset form
  };

  const handleEditCancel = () => {
    setIsEditModalVisible(false);
    setSelectedDbType(''); // reset
    form.resetFields();
  };

    const openDeleteModal = (id, name) => {
    setDeleteId(id);
    setDeleteName(name);
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = async () => {
    setIsDeleteModalOpen(false);
    await deleteConnectionHandler(deleteId);
  };

  const deleteConnectionHandler = async (id) => {
    try {
      await deleteConnection(id);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Connection deleted.');
      fetchConnections();
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    }
  };

  const handleTestConnection = async (id) => {
    setConnections((prev) => prev.map((conn) => (conn.id === id ? { ...conn, test_status: 'loading' } : conn)));

    try {
      const response = await testConnection(id);
      const status = response.status == 200 ? 'true' : 'false';
      setConnections((prev) =>
        prev.map((conn) =>
          conn.id === id
            ? {
                ...conn,
                test_status: status,
                tested_at: new Date().toISOString(),
              }
            : conn
        )
      );
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Connection tested successfully.');
    } catch (error) {
      setConnections((prev) =>
        prev.map((conn) =>
          conn.id === id
            ? {
                ...conn,
                test_status: 'false',
                tested_at: new Date().toISOString(),
              }
            : conn
        )
      );
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || error.message);
    }
  };

  const getActionMenu = (record) => (
    <Menu>
      <Menu.Item key='test' onClick={() => handleTestConnection(record.id)} icon={<ApiFilled />}>
        Test
      </Menu.Item>
      <Menu.Item key='Update' onClick={() => showEditModal(record)} icon={<EditFilled />}>
        Update
      </Menu.Item>
      <Menu.Item key='delete' danger onClick={() => openDeleteModal(record.id, record.name)} icon={<DeleteFilled />}>
        Delete
      </Menu.Item>
    </Menu>
  );

  // Handle DB Change in Form
  const handleDbTypeChange = (value) => {
    setSelectedDbType(value);

    let defaultPort = '';
    let defaultDbName = undefined;

    switch (value) {
      case 'PostgreSQL':
        defaultPort = '5432';
        break;
      case 'MSSQL':
        defaultPort = '1433';
        break;
      case 'MySQL':
        defaultPort = '3306';
        break;
      case 'Redshift':
        defaultPort = '5439';
        break;
      case 'Elasticsearch':
        defaultDbName = undefined; // no DB name required
        break;
      default:
        defaultPort = '';
    }

    form.setFieldsValue({
      port: defaultPort,
      db_name: defaultDbName,
    });
  };

  const handleResize =
    (column) =>
    (e, { size }) => {
      const MIN_WIDTH = 80;
      const newWidth = size.width < MIN_WIDTH ? MIN_WIDTH : size.width;

      setColumns(columns.map((col) => (col.key === column.key ? { ...col, width: newWidth } : col)));
    };

  return (
    <>
      <PageBreadcrumb title='Datasource Connections' />

      {/* Header */}
      <Header className='sticky-header'>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            width: '100%',
          }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <DatabaseOutlined style={{ fontSize: 20, color: 'black' }} />
            <span style={{ fontSize: 20, marginLeft: 10, fontWeight: 'bold' }}>Datasource Connections</span>
          </div>
          <div style={{ marginRight: '8px' }}>
            <Button type='primary' onClick={showCreateModal}>
              Create
            </Button>
          </div>
        </div>
      </Header>

      {loading ? (
        <LoadingWidget />
      ) : (
        <main className='main-container'>
          <Card bordered={false} className='table-card'>
            <Table
              columns={columns}
              components={{ header: { cell: ResizableReact } }}
              dataSource={connections}
              rowKey='id'
              pagination={{
                pageSize,
                showSizeChanger: true,
                onChange: (page, size) => setpageSize(size),
              }}
              size='small'
              sticky
              scroll={{ y: '50vh' }}
              style={{ height: 380 }}
            />
          </Card>
        </main>
      )}

      {/* Create Modal */}
      <Modal
        title='Create Connection'
        visible={isCreateModalVisible}
        onCancel={handleCreateCancel}
        footer={null}
        style={{
          top: '5%',
          transition: 'none',
        }}
        width={600}>
        <Form form={form} onFinish={handleSubmit} onValuesChange={handleValuesChange} layout='vertical' size='middle'>
          {/* Database Type */}
          <Form.Item
            label={
              <span>
                Type <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='type'
            rules={[{ message: 'Type is required.' }]}
            style={{ marginBottom: 4, marginTop: -20 }}>
            <Select placeholder='Select database type' allowClear onChange={handleDbTypeChange} loading={!Array.isArray(supportedConnection) || supportedConnection.length === 0}>
              {(Array.isArray(supportedConnection) ? supportedConnection : []).map((conn) => (
                <Option key={conn.name} value={conn.name}>
                  {conn.display_name}
                </Option>
              ))}
            </Select>
          </Form.Item>

          {/* Connection Name */}
          <Form.Item
            label={
              <span>
                Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Name is required.' }, { validator: validateFourChar }]}
            style={{ marginBottom: 4 }}>
            <Input placeholder='Enter connection name' />
          </Form.Item>

          {/* Databricks Fields */}
          {selectedDbType === 'Databricks' && (
            <>
              <Form.Item
                label={
                  <span>
                    Host <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='host'
                rules={[{ message: 'Host is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter Databricks host' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    HTTP Path <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='http_path'
                rules={[{ message: 'HTTP path is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter HTTP path' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    Access Token <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='access_token'
                rules={[{ message: 'Access token is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input.Password placeholder='Enter Databricks access token' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    Catalog <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='catalog'
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter catalog name' />
              </Form.Item>
            </>
          )}

          {selectedDbType !== 'Databricks' && (
            <>
              {/* Host */}
              <Form.Item
                label={
                  <span>
                    Hostname/Address <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='host'
                rules={[{ message: 'Please input server hostname.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter hostname or server IP address' />
              </Form.Item>

              {selectedDbType === 'Elasticsearch' && (
                <>
                  <Form.Item
                    label={
                      <span>
                        Authentication Type <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='auth_type'
                    rules={[{ message: 'Authentication type is required.' }]}
                    style={{ marginBottom: 4 }}>
                    <Select placeholder='Select authentication type'>
                      <Option value='http_auth'>HTTP Auth</Option>
                      <Option value='basic_auth'>Basic Auth</Option>
                    </Select>
                  </Form.Item>
                </>
              )}

              {/* Port - always shown */}
              <Form.Item
                label={<span>Port{selectedDbType !== 'Elasticsearch' && <span style={{ color: 'red' }}> *</span>}</span>}
                name='port'
                rules={
                  selectedDbType === 'Elasticsearch'
                    ? [] // no validation rules for Elasticsearch
                    : [
                        { message: 'Please input server port.' },
                        () => ({
                          validator(_, value) {
                            if (!value || (Number(value) >= 1 && Number(value) <= 65535)) {
                              return Promise.resolve();
                            }
                            return Promise.reject(new Error('Port number must be between 1 and 65535.'));
                          },
                        }),
                      ]
                }
                style={{ marginBottom: 4 }}>
                <Input placeholder={selectedDbType === 'Elasticsearch' ? 'Enter Elasticsearch port (optional)' : 'Enter database port'} />
              </Form.Item>

              {/* Database Name - only for non-Elasticsearch */}
              {selectedDbType !== 'Elasticsearch' && (
                <Form.Item
                  label={
                    <span>
                      Database Name <span style={{ color: 'red' }}>*</span>
                    </span>
                  }
                  name='db_name'
                  rules={[{ message: 'Please input Database name.' }]}
                  style={{ marginBottom: 4 }}>
                  <Input placeholder='Enter database name' />
                </Form.Item>
              )}

              {/* Username */}
              <Form.Item
                label={
                  <span>
                    Username <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='user_name'
                rules={[{ message: 'Username is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder={selectedDbType === 'Elasticsearch' ? 'Enter Elasticsearch username' : 'Enter database username'} />
              </Form.Item>

              {/* Password */}
              <Form.Item
                label={
                  <span>
                    Password <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='password'
                rules={[{ message: 'Password is required.' }]}
                style={{ marginBottom: 10 }}>
                <Input.Password placeholder='Enter password' />
              </Form.Item>

              {selectedDbType === 'Elasticsearch' && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <label style={{ margin: 0 }}>SSL Connection</label>
                  <Form.Item name='is_secure' valuePropName='checked' initialValue={false} noStyle>
                    <Switch checkedChildren='Yes' unCheckedChildren='No' className='elasticsearch-switch' />
                  </Form.Item>
                </div>
              )}
            </>
          )}

          {/* Submit Button */}
          <Form.Item style={{ marginBottom: -15 }}>
            <Button type='primary' htmlType='submit' disabled={isButtonDisabled} style={{ float: 'right', width: '20%' }}>
              Create
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Update Modal */}
      <Modal
        title='Update Connection'
        visible={isEditModalVisible}
        onCancel={handleEditCancel}
        footer={null}
        style={{
          top: '5%',
          transition: 'none',
        }}
        width={600}>
        <Form form={form} onFinish={handleSubmit} onValuesChange={handleEditValuesChange} layout='vertical' size='middle'>
          <Form.Item
            label={
              <span>
                Type <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='type'
            rules={[{ message: 'Type is required.' }]}
            style={{ marginBottom: 4, marginTop: -20 }}>
            <Select placeholder='Select database type' allowClear onChange={handleDbTypeChange} loading={!Array.isArray(supportedConnection) || supportedConnection.length === 0}>
              {(Array.isArray(supportedConnection) ? supportedConnection : []).map((conn) => (
                <Option key={conn.name} value={conn.name}>
                  {conn.display_name}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label={
              <span>
                Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Name is required.' }, { validator: validateFourChar }]}
            style={{ marginBottom: 4 }}>
            <Input placeholder='Enter connection name' />
          </Form.Item>

          {/* Databricks Fields */}
          {selectedDbType === 'Databricks' && (
            <>
              <Form.Item
                label={
                  <span>
                    Host <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='host'
                rules={[{ message: 'Host is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter Databricks host' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    HTTP Path <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='http_path'
                rules={[{ message: 'HTTP path is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter HTTP path' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    Access Token <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='access_token'
                rules={[{ message: 'Access token is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input.Password placeholder='Enter Databricks access token' />
              </Form.Item>

              <Form.Item
                label={
                  <span>
                    Catalog<span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='catalog'
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter catalog name' />
              </Form.Item>
            </>
          )}

          {selectedDbType !== 'Databricks' && (
            <>
              {' '}
              <Form.Item
                label={
                  <span>
                    Hostname/Address <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='host'
                rules={[{ message: 'Please input server hostname.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder='Enter hostname or server IP address' />
              </Form.Item>

              {selectedDbType === 'Elasticsearch' && (
                <>
                  <Form.Item
                    label={
                      <span>
                        Authentication Type <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='auth_type'
                    rules={[{ message: 'Authentication type is required.' }]}
                    style={{ marginBottom: 4 }}>
                    <Select placeholder='Select authentication type'>
                      <Option value='http_auth'>HTTP Auth</Option>
                      <Option value='basic_auth'>Basic Auth</Option>
                    </Select>
                  </Form.Item>
                </>
              )}
              <Form.Item
                label={<span>Port{selectedDbType !== 'Elasticsearch' && <span style={{ color: 'red' }}> *</span>}</span>}
                name='port'
                rules={
                  selectedDbType === 'Elasticsearch'
                    ? [] // no validation rules for Elasticsearch
                    : [
                        { message: 'Please input server port.' },
                        () => ({
                          validator(_, value) {
                            if (!value || (Number(value) >= 1 && Number(value) <= 65535)) {
                              return Promise.resolve();
                            }
                            return Promise.reject(new Error('Port number must be between 1 and 65535.'));
                          },
                        }),
                      ]
                }
                style={{ marginBottom: 4 }}>
                <Input placeholder={selectedDbType === 'Elasticsearch' ? 'Enter Elasticsearch port (optional)' : 'Enter database port'} />
              </Form.Item>
              {selectedDbType !== 'Elasticsearch' && (
                <Form.Item
                  label={
                    <span>
                      Database Name <span style={{ color: 'red' }}>*</span>
                    </span>
                  }
                  name='db_name'
                  rules={[{ message: 'Please input Database name.' }]}
                  style={{ marginBottom: 4 }}>
                  <Input placeholder='Enter database name' />
                </Form.Item>
              )}
              <Form.Item
                label={
                  <span>
                    Username <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='user_name'
                rules={[{ message: 'Username is required.' }]}
                style={{ marginBottom: 4 }}>
                <Input placeholder={selectedDbType === 'Elasticsearch' ? 'Enter Elasticsearch username' : 'Enter database username'} />
              </Form.Item>
              <Form.Item
                label={
                  <span>
                    Password <span style={{ color: 'red' }}>*</span>
                  </span>
                }
                name='password'
                rules={[{ message: 'Password is required.' }]}
                style={{ marginBottom: 10 }}>
                <Input.Password placeholder='Enter database password' />
              </Form.Item>
              {selectedDbType === 'Elasticsearch' && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <label style={{ margin: 0 }}>SSL Connection</label>
                  <Form.Item name='is_secure' valuePropName='checked' initialValue={false} noStyle>
                    <Switch checkedChildren='Yes' unCheckedChildren='No' className='elasticsearch-switch' />
                  </Form.Item>
                </div>
              )}
            </>
          )}

          <Form.Item style={{ marginBottom: -15 }}>
            <Button type='primary' htmlType='submit' disabled={isEditButtonDisabled} style={{ float: 'right', width: '20%' }}>
              Update
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* DELETE CONFIRMATION MODAL */}
      <Modal open={isDeleteModalOpen} onCancel={() => setIsDeleteModalOpen(false)} footer={null} title='Delete Confirmation'>
        <div style={{ marginBottom: 20 }}>
          Are you sure you want to delete&nbsp;
          <strong>'{deleteName}'</strong> connection?
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Button onClick={() => setIsDeleteModalOpen(false)}>Cancel</Button>

          <Button
            danger
            type='primary'
            onClick={handleConfirmDelete}
            style={{
              background: '#ff4d4f',
              borderColor: '#ff4d4f',
              color: '#fff',
            }}>
            Delete
          </Button>
        </div>
      </Modal>

      <style>
        {`
          .table-card .ant-card-body {
            min-height: 68vh !important;
          }
          
          /* Elasticsearch Switch Styling */
          .elasticsearch-switch.ant-switch.ant-switch-checked {
            background: #52c41a !important;
          }
          
          .elasticsearch-switch.ant-switch.ant-switch-checked:hover:not(.ant-switch-disabled) {
            background: #73d13d !important;
          }
          
          .elasticsearch-switch.ant-switch {
            background: rgba(0, 0, 0, 0.25);
          }
      `}
      </style>
    </>
  );
};

export default Connections;
