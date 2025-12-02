import dayjs from 'dayjs';
import React, { useState, useEffect } from 'react';
import { Switch, Space, Select, Button, Form, Input, Table, Modal, notification, Layout, Dropdown, Menu, Typography, Tag, Card, Tooltip } from 'antd';
import { EllipsisOutlined, EditFilled, DeleteFilled, CloseCircleOutlined, CheckCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { createNewModel, editModel, getAllModels, deleteModel, getModelById, updateModelStatus, updateDefaultStatus, getSupportedModel, getModelSettingsTemplate } from '../../services/ProductApis';
import LoadingWidget from '../../components/LoadingWidget';
import ml_icon from '../../assets/imgs/ml_icon.png';
import { validateFourChar } from '../../utils/validation/validateFourChar';
import ResizableReact from '../../components/ResizeableReact';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';

const { Header } = Layout;
const { Text } = Typography;

const ModelManagement = () => {
  const [models, setModels] = useState([]);
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [isEditButtonDisabled, setEditIsButtonDisabled] = useState(false);
  const [pageSize, setpageSize] = useState(10);
  const [isActive, setIsActive] = useState(true);
  const [isDefault, setIsDefault] = useState('');
  const [supportedModels, setSupportedModels] = useState([]);
  const [settingsTemplate, setSettingsTemplate] = useState(null);
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
      width: 80,
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
      render: (name, record) => (
        <>
          {record.is_default && <span style={{ color: 'green', marginRight: 5 }}>✅</span>}
          {name}
        </>
      ),
    },
    {
      title: <div style={{ textAlign: 'center' }}>Description</div>,
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      width: 200,
    },
    {
      title: 'Type',
      dataIndex: 'type',
      align: 'center',
      key: 'type',
      width: 150,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Status',
      dataIndex: 'is_active',
      align: 'center',
      key: 'is_active',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (isActive) => <Tag color={isActive ? 'green' : 'red'}>{isActive ? 'Active' : 'Inactive'}</Tag>,
      width: 100,
    },
    {
      title: 'Default',
      dataIndex: 'is_default',
      align: 'center',
      key: 'is_default',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (isDefault) => <Text>{isDefault ? 'True' : 'False'}</Text>,
      width: 100,
    },
    {
      title: 'Created At',
      dataIndex: 'created_at',
      align: 'center',
      key: 'created_at',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text) => {
        const formattedDate = dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : '';
        return <Text>{formattedDate}</Text>;
      },
      width: 150,
    },
    {
      title: 'Updated At',
      dataIndex: 'updated_at',
      align: 'center',
      key: 'updated_at',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text) => {
        const formattedDate = dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : '';
        return <Text>{formattedDate}</Text>;
      },
      width: 150,
    },
    {
      title: 'Actions',
      align: 'center',
      key: 'actions',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text, record) => (
        <Dropdown overlay={getActionMenu(record)} trigger={['click']}>
          <Button type='text' icon={<EllipsisOutlined rotate={90} />} />
        </Dropdown>
      ),
      width: 80,
    },
  ]);

  const [form] = Form.useForm();

  // Fetch all models on component mount
  useEffect(() => {
    fetchModels();
    fetchSupportedModels();
  }, []);

  useEffect(() => {
    if (currentRecord) {
      const matchedModel = supportedModels.find((m) => m.display_name === currentRecord.type || m.name === currentRecord.type);

      form.setFieldsValue({
        name: currentRecord.name,
        type: matchedModel?.display_name,
        description: currentRecord.description,
        settings: currentRecord.data ? atob(currentRecord.data) : '',
      });

      setIsDefault(currentRecord.is_default);
    }
  }, [currentRecord, supportedModels, form]);

  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };
  const showNotification = (icon, message, description) => {
    notification.open({
      ...notificationProps,
      icon,
      message,
      description,
    });
  };

  // Fetch all models from the API
  const fetchModels = async () => {
    setLoading(true);
    try {
      const response = await getAllModels();
      setModels(response.data);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    } finally {
      setLoading(false);
    }
  };

  const fetchSupportedModels = async () => {
    setLoading(true);
    try {
      const response = await getSupportedModel();
      setSupportedModels(response.data);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    } finally {
      setLoading(false);
    }
  };

  // Fetch model settings template - now using display_name as ModelName
  const fetchModelSettingsTemplate = async (displayName) => {
    try {
      const response = await getModelSettingsTemplate(displayName);
      setSettingsTemplate(response.data);
      return response.data;
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch settings template');
      return null;
    }
  };

  // Function to handle creating a new model
  const handleCreate = async (values) => {
    setLoading(true);
    try {
      const data = {
        name: values.name,
        type: values.type, // This is the display_name
        description: values.description || '',
        settings: form.getFieldValue('settings') !== undefined ? btoa(form.getFieldValue('settings')) : '',
        is_active: true,
      };

      await createNewModel(data);
      fetchModels();
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'LLM Model created successfully.');

      setIsCreateModalVisible(false);
    } catch (error) {
      const errorMessage = error?.response?.data?.error || 'Failed to create the model.';
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Function to handle updating an existing model
  const handleUpdate = async (values) => {
    setLoading(true);
    try {
      const data = {
        name: values.name,
        type: values.type, // This is the display_name
        description: values.description || '',
        settings: form.getFieldValue('settings') !== undefined ? btoa(form.getFieldValue('settings')) : '',
        is_active: isActive,
        is_default: isDefault,
      };

      await editModel(currentRecord.id, data);
      fetchModels();
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `LLM Configuration with id ${currentRecord.id} updated successfully.`);

      setIsEditModalVisible(false);
      setCurrentRecord(null);
    } catch (error) {
      const errorMessage = error?.response?.data?.error || 'Failed to update the model.';
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', errorMessage);
    } finally {
      setLoading(false);
    }
  };

  // Handle type change in create modal - now using display_name directly
  const handleTypeChangeCreate = async (displayName) => {
    if (displayName) {
      const template = await fetchModelSettingsTemplate(displayName);
      if (template) {
        form.setFieldsValue({
          settings: JSON.stringify(template, null, 2),
        });
      }
    }
  };

  // Handle type change in edit modal - now using display_name directly
  const handleTypeChangeEdit = async (displayName) => {
    if (displayName) {
      const template = await fetchModelSettingsTemplate(displayName);
      if (template) {
        form.setFieldsValue({
          settings: JSON.stringify(template, null, 2),
        });
      }
    }
  };

  // Generic validation function that uses the settings template
  const validateSettings = (settings, template) => {
    if (!settings || !template) return { valid: false, error: 'Settings or template missing.' };

    try {
      const parsedValue = JSON.parse(settings);

      const templateKeys = Object.keys(template);
      const parsedKeys = Object.keys(parsedValue);

      // Find missing keys
      const missingKeys = templateKeys.filter((key) => !(key in parsedValue));

      // Find keys that exist but have empty values
      const emptyKeys = templateKeys.filter((key) => {
        const val = parsedValue[key];
        if (typeof val === 'string') return val.trim() === '';
        return val === null || val === undefined;
      });

      if (missingKeys.length > 0) {
        return {
          valid: false,
          error: `Missing required fields: ${missingKeys.join(', ')}`,
        };
      }

      if (emptyKeys.length > 0) {
        return {
          valid: false,
          error: `Fields cannot be empty: ${emptyKeys.join(', ')}`,
        };
      }

      return { valid: true };
    } catch (e) {
      return { valid: false, error: 'Invalid JSON format.' };
    }
  };

  const handleValuesChange = (changedValues, allValues) => {
    const { name, type, settings } = allValues;

    const isNameValid = name?.trim().length >= 4;

    let allRequiredFieldsFilled = name?.trim() && type?.trim() && settings?.trim();

    if (settings && settingsTemplate) {
      const result = validateSettings(settings, settingsTemplate);
      if (!result.valid) {
        allRequiredFieldsFilled = false;
      }
    } else if (settings && !settingsTemplate) {
      try {
        JSON.parse(settings);
      } catch (e) {
        allRequiredFieldsFilled = false;
      }
    }
    setIsButtonDisabled(!(allRequiredFieldsFilled && isNameValid));
  };

  // Handle form field changes for edit modal
  const handleValuesChangeEdit = (changedValues, allValues) => {
    const { name, type, settings } = allValues;
    const isNameValid = name?.trim().length >= 4;
    let allRequiredFieldsFilled = name?.trim() && type?.trim() && settings?.trim();

    if (settings && settingsTemplate) {
      const result = validateSettings(settings, settingsTemplate);
      if (!result.valid) {
        allRequiredFieldsFilled = false;
      }
    } else if (settings && !settingsTemplate) {
      try {
        JSON.parse(settings);
      } catch (e) {
        allRequiredFieldsFilled = false;
      }
    }
    setEditIsButtonDisabled(!(allRequiredFieldsFilled && isNameValid));
  };

  const showCreateModal = async () => {
    setCurrentRecord(null);
    setIsCreateModalVisible(true);
    form.resetFields();

    // Fetch template for the first supported model when modal opens
    if (supportedModels.length > 0) {
      const firstModel = supportedModels[0];
      const template = await fetchModelSettingsTemplate(firstModel.display_name);
      if (template) {
        form.setFieldsValue({
          type: firstModel.display_name,
          settings: JSON.stringify(template, null, 2),
        });
      }
    }
  };

  // Open modal for editing a model
  const showEditModal = async (record) => {
    setIsEditModalVisible(true);
    setCurrentRecord(null);

    try {
      const response = await getModelById(record.id);
      setCurrentRecord(response.data);

      // Fetch settings template for the current model type using display_name
      await fetchModelSettingsTemplate(response.data.type);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    }
  };

  // Close the create modal
  const handleCreateCancel = () => {
    setIsCreateModalVisible(false);
    form.resetFields();
    setSettingsTemplate(null);
  };

  // Close the edit modal
  const handleEditCancel = () => {
    setIsEditModalVisible(false);
    setCurrentRecord(null);
    setSettingsTemplate(null);
  };

  // Delete a model
  const deleteModelHandler = async (id) => {
    try {
      await deleteModel(id);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'LLM Configuration deleted successfully.');
      fetchModels();
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    }
  };

    const openDeleteModal = (id, name) => {
      setDeleteId(id);
      setDeleteName(name);
      setIsDeleteModalOpen(true);
    };

    const handleConfirmDelete = async () => {
      setIsDeleteModalOpen(false);
      await deleteModelHandler(deleteId);
    };

  // To Manage active or inactive status
  const updateStatusHandler = async (record) => {
    setLoading(true);
    try {
      const updatedStatus = !record.is_active;
      const response = await updateModelStatus({ status: updatedStatus }, record.id);

      if (response.status !== 200) {
        throw new Error('Failed to update status');
      }

      setTimeout(() => {
        setIsActive(updatedStatus);
      }, 1000);

      await fetchModels();

      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Model ${record.name} status updated to ${updatedStatus ? 'Active' : 'Inactive'}.`);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    } finally {
      setLoading(false);
    }
  };

  // Toggle the model's default status
  const updateDefaultHandler = async (record) => {
    setLoading(true);
    try {
      const updatedStatus = !record.is_active;
      const response = await updateDefaultStatus({ status: updatedStatus }, record.id);

      if (response.status !== 200) {
        throw new Error('Failed to update status');
      }

      setIsDefault(updatedStatus);
      await fetchModels();

      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Model ${record.name} default status updated.`);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', `Failed to update the status of model ${record.name}.`);
    } finally {
      setLoading(false);
    }
  };

  // Action menu for each row
  const getActionMenu = (record) => (
    <Menu>
      <Menu.Item key='edit' onClick={() => showEditModal(record)} icon={<EditFilled />}>
        <span style={{ marginLeft: 10 }}>Update</span>
      </Menu.Item>
      <Menu.Item
        key='toggle-default-status'
        onClick={() => {
          if (!record.is_default) {
            updateDefaultHandler(record);
          }
        }}
        disabled={record.is_default}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Switch
            size='small'
            style={{ transform: 'scale(0.6)', marginLeft: '-5px' }}
            checked={record.is_default}
            disabled={record.is_default}
            onChange={(checked, event) => {
              event.stopPropagation();
              if (!record.is_default) {
                updateDefaultHandler(record);
              }
            }}
          />
          <span style={{ marginLeft: '8px' }}>{record.is_default ? 'Default' : 'Set as Default'}</span>
        </div>
      </Menu.Item>
      <Menu.Item key='toggle-active-status' onClick={() => updateStatusHandler(record)}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Switch size='small' style={{ transform: 'scale(0.6)', marginLeft: '-5px' }} checked={record.is_active} />
          <span style={{ marginLeft: '8px' }}>{record.is_active ? 'Inactive' : 'Active'}</span>
        </div>
      </Menu.Item>
      <Menu.Item key='delete' danger onClick={() => openDeleteModal(record.id, record.name)} icon={<DeleteFilled />}>
        <span style={{ marginLeft: 10 }}>Delete</span>
      </Menu.Item>
    </Menu>
  );

  const handleResize =
    (column) =>
    (e, { size }) => {
      const MIN_WIDTH = 80;
      const newWidth = size.width < MIN_WIDTH ? MIN_WIDTH : size.width;

      const newColumns = columns.map((col) => {
        if (col.key === column.key) {
          return { ...col, width: newWidth };
        }
        return col;
      });
      setColumns(newColumns);
    };

  return (
    <>
      <PageBreadcrumb title='LLM Configurations' />

      {/* Header */}
      <Header className='sticky-header'>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
          }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
            }}>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <img src={ml_icon} alt='LLM Configurations Icon' style={{ width: 20, height: 20 }} />
              <span
                style={{
                  fontSize: '20px',
                  color: 'black',
                  marginLeft: '10px',
                  fontWeight: 'bold',
                }}>
                LLM Configurations
              </span>
            </div>
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
              components={{
                header: {
                  cell: ResizableReact,
                },
              }}
              columns={columns}
              dataSource={models}
              rowKey='id'
              pagination={{
                pageSize: pageSize,
                showSizeChanger: true,
                onChange: (page, pageSize) => {
                  setpageSize(pageSize);
                },
              }}
              size='small'
              sticky
              scroll={{ y: '50vh' }}
              style={{ height: 380 }}
            />
          </Card>
        </main>
      )}

      {/* Create Modal for Creating a Model */}
      <Modal
        title='Create LLM Configuration'
        visible={isCreateModalVisible}
        onCancel={handleCreateCancel}
        footer={null}
        style={{ top: '5%', transition: 'none' }}
        afterClose={() => form.resetFields()}
        width={600}>
        <Form form={form} onFinish={handleCreate} onValuesChange={handleValuesChange} layout='vertical' size='medium' style={{ marginTop: '-20px', marginBottom: '-18px' }}>
          {/* Type Field */}
          <Form.Item
            label={
              <span>
                Type <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='type'
            rules={[{ message: 'Please input the type.' }]}
            style={{ marginBottom: 5 }}>
            <Select placeholder='Select type' loading={loading} onChange={handleTypeChangeCreate}>
              {supportedModels?.map((model) => (
                <Select.Option key={model.display_name} value={model.display_name}>
                  {model.display_name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {/* Name Field */}
          <Form.Item
            label={
              <span>
                Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Please input the name.' }, { validator: validateFourChar }]}
            style={{ marginBottom: 5 }}>
            <Input placeholder='Enter name' />
          </Form.Item>

          {/* Description Field */}
          <Form.Item label='Description' name='description' rules={[{ message: 'Please input the description.' }]} style={{ marginBottom: 5 }}>
            <Input.TextArea placeholder='Enter description' style={{ height: '50px', overflowY: 'auto', resize: 'none' }} />
          </Form.Item>

          {/* Settings Field */}
          <Form.Item
            label={
              <span>
                Settings
                <Tooltip title='Enter your model settings in JSON format. The template will be automatically loaded based on the selected model type.'>
                  <InfoCircleOutlined style={{ marginLeft: 8, color: 'black' }} />
                </Tooltip>
              </span>
            }
            name='settings'
            rules={[
              {
                message: 'Please input the settings.',
              },
              {
                validator: (_, value) => {
                  if (!value || value.trim() === '') {
                    return Promise.reject('Settings cannot be empty.');
                  }
                  try {
                    const parsed = JSON.parse(value);

                    if (settingsTemplate) {
                      const templateKeys = Object.keys(settingsTemplate);
                      const missingKeys = templateKeys.filter((key) => !(key in parsed));
                      const emptyKeys = templateKeys.filter((key) => {
                        const val = parsed[key];
                        if (typeof val === 'string') return val.trim() === '';
                        return val === null || val === undefined;
                      });

                      if (missingKeys.length > 0) return Promise.reject(`Missing required fields: ${missingKeys.join(', ')}`);

                      if (emptyKeys.length > 0) return Promise.reject(`Fields cannot be empty: ${emptyKeys.join(', ')}`);
                    }

                    return Promise.resolve();
                  } catch {
                    return Promise.reject('Invalid JSON format.');
                  }
                },
              },
            ]}>
            <Input.TextArea
              placeholder='Enter settings JSON'
              style={{
                height: '200px',
                overflowY: 'auto',
                resize: 'none',
                fontFamily: 'monospace',
              }}
            />
          </Form.Item>

          {/* Save Button */}
          <div
            className='button-container'
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              marginBottom: 4,
            }}>
            <Space direction='horizontal' size={10}>
              <Button
                disabled={isButtonDisabled}
                type='primary'
                onClick={() => {
                  form.submit();
                  setTimeout(() => {
                    setIsCreateModalVisible(false);
                  }, 0);
                }}>
                Save
              </Button>
            </Space>
          </div>
        </Form>
      </Modal>

      {/* Update Modal for Updating a Model */}
      <Modal
        title='Update LLM Configuration'
        visible={isEditModalVisible}
        onCancel={handleEditCancel}
        footer={null}
        style={{ top: '5%', transition: 'none' }}
        afterClose={() => form.resetFields()}
        width={600}>
        <Form form={form} onFinish={handleUpdate} onValuesChange={handleValuesChangeEdit} layout='vertical' size='medium' style={{ marginTop: '-20px', marginBottom: '-18px' }}>
          {/* Type Field */}
          <Form.Item
            label={
              <span>
                Type <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='type'
            rules={[{ message: 'Please input the type.' }]}
            style={{ marginBottom: 5 }}>
            <Select placeholder='Select type' loading={loading} onChange={handleTypeChangeEdit}>
              {supportedModels?.map((model) => (
                <Select.Option key={model.display_name} value={model.display_name}>
                  {model.display_name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {/* Name Field */}
          <Form.Item
            label={
              <span>
                Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Please input the name.' }]}
            style={{ marginBottom: 5 }}>
            <Input placeholder='Enter name' />
          </Form.Item>

          {/* Description Field */}
          <Form.Item label='Description' name='description' rules={[{ message: 'Please input the description.' }]} style={{ marginBottom: 5 }}>
            <Input.TextArea placeholder='Enter description' style={{ height: '50px', overflowY: 'auto', resize: 'none' }} />
          </Form.Item>

          {/* Settings Field */}
          <Form.Item
            label={
              <span>
                Settings
                <Tooltip title='Enter your model settings in JSON format. The template will be automatically loaded based on the selected model type.'>
                  <InfoCircleOutlined style={{ marginLeft: 8, color: 'black' }} />
                </Tooltip>
              </span>
            }
            name='settings'
            rules={[
              {
                message: 'Please input the settings.',
              },
              {
                validator: (_, value) => {
                  if (!value || value.trim() === '') {
                    return Promise.reject('Settings cannot be empty.');
                  }
                  try {
                    const parsed = JSON.parse(value);

                    if (settingsTemplate) {
                      const templateKeys = Object.keys(settingsTemplate);
                      const missingKeys = templateKeys.filter((key) => !(key in parsed));
                      const emptyKeys = templateKeys.filter((key) => {
                        const val = parsed[key];
                        if (typeof val === 'string') return val.trim() === '';
                        return val === null || val === undefined;
                      });

                      if (missingKeys.length > 0) return Promise.reject(`Missing required fields: ${missingKeys.join(', ')}`);

                      if (emptyKeys.length > 0) return Promise.reject(`Fields cannot be empty: ${emptyKeys.join(', ')}`);
                    }

                    return Promise.resolve();
                  } catch {
                    return Promise.reject('Invalid JSON format.');
                  }
                },
              },
            ]}>
            <Input.TextArea
              placeholder='Enter settings JSON'
              style={{
                height: '200px',
                overflowY: 'auto',
                resize: 'none',
                fontFamily: 'monospace',
              }}
            />
          </Form.Item>

          {/* Update Button */}
          <div
            className='button-container'
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              marginBottom: 4,
            }}>
            <Space direction='horizontal' size={10}>
              <Button
                disabled={isEditButtonDisabled}
                type='primary'
                onClick={() => {
                  form.submit();
                  setTimeout(() => {
                    setIsEditModalVisible(false);
                  }, 0);
                }}>
                Update
              </Button>
            </Space>
          </div>
        </Form>
      </Modal>

      {/* DELETE CONFIRMATION MODAL */}
      <Modal open={isDeleteModalOpen} onCancel={() => setIsDeleteModalOpen(false)} footer={null} title='Delete Confirmation'>
        <div style={{ marginBottom: 20 }}>
          Are you sure you want to delete&nbsp;
          <strong>'{deleteName}'</strong> configuration?
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
        `}
      </style>
    </>
  );
};

export default ModelManagement;
