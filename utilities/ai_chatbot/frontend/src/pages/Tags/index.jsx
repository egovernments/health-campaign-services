import dayjs from 'dayjs';
import React, { useState, useEffect } from 'react';
import LoadingWidget from '../../components/LoadingWidget';
import ResizableReact from '../../components/ResizeableReact';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';
import { validateFourChar } from '../../utils/validation/validateFourChar';
import { createNewTag, editTag, getAllTags, deleteTag, getTagsById } from '../../services/ProductApis';
import { Button, Form, Input, Table, Modal, notification, Layout, Dropdown, Menu, Typography, Tag, Card } from 'antd';
import { DatabaseOutlined, EllipsisOutlined, EditFilled, DeleteFilled, CloseCircleOutlined, CheckCircleOutlined } from '@ant-design/icons';

const { Header } = Layout;
const { Text } = Typography;

const Tags = () => {
  const [tags, setTags] = useState([]);
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [currentTag, setCurrentTag] = useState(null);
  const [loading, setLoading] = useState(false);
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [isEditButtonDisabled, setEditIsButtonDisabled] = useState(false);
  const [pageSize, setpageSize] = useState(10);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteId, setDeleteId] = useState(null);
  const [deleteName, setDeleteName] = useState('');

  const [columns, setColumns] = useState([
    { title: 'ID', dataIndex: 'id', key: 'id', align: 'center', onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }), width: 80 },
    {
      title: <div style={{ textAlign: 'center' }}>Name</div>,
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
      width: 200,
    },
    {
      title: <div style={{ textAlign: 'center' }}>Description</div>,
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
      width: 300,
    },
    {
      title: 'Status',
      dataIndex: 'is_active',
      align: 'center',
      key: 'is_active',
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
      render: (isActive) => <Tag color={isActive ? 'green' : 'red'}>{isActive ? 'Active' : 'Inactive'}</Tag>,
      width: 100,
    },
    {
      title: 'Created At',
      dataIndex: 'created_at',
      align: 'center',
      key: 'created_at',
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
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
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
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
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
      render: (text, record) => (
        <Dropdown overlay={getActionMenu(record)} trigger={['click']}>
          <Button type='text' icon={<EllipsisOutlined rotate={90} />} />
        </Dropdown>
      ),
      width: 80,
    },
  ]);

  const [form] = Form.useForm();

  // For Notification
  const notificationProps = { className: 'custom-class', style: { width: 350 } };
  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  // Useeffects
  useEffect(() => {
    fetchTags();
  }, []);

  useEffect(() => {
    if (currentTag) {
      form.setFieldsValue({ name: currentTag.name, description: currentTag.description });
    }
  }, [currentTag, form]);

  // Fetch all tags from the API
  const fetchTags = async () => {
    setLoading(true);
    try {
      const response = await getAllTags();
      if (response) {
        setTags(response.data);
      }
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response ? error?.response?.data?.error : error?.message);
    } finally {
      setLoading(false);
    }
  };

  // Handle form submission for creating or updating a tag
  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      // If no description is provided, set it to null or 'none'
      const description = values.description || '';

      if (currentTag) {
        // Update tag
        const data = {
          name: values.name,
          description, // Use the description from the form (or null)
          is_active: true,
        };
        await editTag(currentTag.id, data);
        showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Tag with id ${currentTag.id} updated successfully.`);
      } else {
        // Create new tag
        const data = { name: values.name, description, is_active: true };
        await createNewTag(data);
        showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Tag created successfully.');
      }
      setIsCreateModalVisible(false);
      setIsEditModalVisible(false);
      setCurrentTag(null);
      fetchTags();
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response ? error?.response?.data?.error : error?.message);
    } finally {
      setLoading(false);
    }
  };

  const handleValuesChange = (changedValues, allValues) => {
    const { name } = allValues;
    setIsButtonDisabled(!(name && name.trim()));
  };

  const handleValuesChangeEdit = (changedValues, allValues) => {
    const { name } = allValues;
    setEditIsButtonDisabled(!(name && name.trim()));
  };

  // Open modal for creating a tag
  const showCreateModal = () => {
    setCurrentTag(null); // Ensure currentTag is null for creating
    setIsCreateModalVisible(true);
    form.resetFields();
  };

  // Open modal for editing a tag
  const showEditModal = async (record) => {
    setIsEditModalVisible(true);
    setCurrentTag(null); // Clear previous data

    // Fetch tag data from the API
    try {
      const response = await getTagsById(record.id);
      setCurrentTag(response.data);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response ? error?.response?.data?.error : error?.message);
    }
  };

  // Close the create modal
  const handleCreateCancel = () => {
    setIsCreateModalVisible(false);
    form.resetFields();
  };

  // Close the edit modal
  const handleEditCancel = () => {
    setIsEditModalVisible(false);
    setCurrentTag(null);
  };

  // Delete a tag
  const deleteTagHandler = async (id) => {
    try {
      await deleteTag(id);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Tag deleted successfully.');
      fetchTags();
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response ? error?.response?.data?.error : error?.message);
    }
  };

    const openDeleteModal = (id, name) => {
      setDeleteId(id);
      setDeleteName(name);
      setIsDeleteModalOpen(true);
    };

    const handleConfirmDelete = async () => {
      setIsDeleteModalOpen(false);
      await deleteTagHandler(deleteId);
    };

  // Action menu for each row
  const getActionMenu = (record) => (
    <Menu>
      <Menu.Item key='edit' onClick={() => showEditModal(record)} icon={<EditFilled />}>
        Update
      </Menu.Item>
      <Menu.Item key='delete' danger onClick={() => openDeleteModal(record.id, record.name)} icon={<DeleteFilled />}>
        Delete
      </Menu.Item>
      {/* <Menu.Item key="toggle-status" icon={<Switch style={{ transform: "scale(0.6)", marginLeft: "-5px" }} onChange={() => toggleTagStatus(record)} />}>
        {record.is_active ? 'Active' : 'Inactive'}
      </Menu.Item> */}
    </Menu>
  );

  const handleResize =
    (column) =>
    (e, { size }) => {
      const MIN_WIDTH = 80;
      const newWidth = size.width < MIN_WIDTH ? MIN_WIDTH : size.width; // Ensure width doesn't go below MIN_WIDTH

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
      <PageBreadcrumb title='Tag Management' />

      {/*  Header */}
      <Header className='sticky-header'>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <DatabaseOutlined style={{ fontSize: '20px', color: 'black' }} />
            <span style={{ fontSize: '20px', color: 'black', marginLeft: '10px', fontWeight: 'bold' }}>Tag Management</span>
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
              dataSource={tags}
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

      {/* Create Modal for Creating a Tag */}
      <Modal form={form} title='Create Tag' visible={isCreateModalVisible} onCancel={handleCreateCancel} footer={null} bodyStyle={{ paddingTop: 0, paddingBottom: 5 }}>
        <Form
          form={form}
          onFinish={handleSubmit}
          onValuesChange={handleValuesChange} // Track field changes
          layout='vertical'>
          <Form.Item
            label={
              <span>
                Tag Name <span style={{ color: 'red' }}>*</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Please input the tag name.' }, { validator: validateFourChar }]}>
            <Input placeholder='Enter tag name' />
          </Form.Item>
          <Form.Item label={'Description'} name='description' rules={[{ message: 'Please input the description.' }]}>
            <Input.TextArea placeholder='Enter tag description' style={{ height: '120px', overflowY: 'auto', resize: 'none' }} />
          </Form.Item>
          <Form.Item>
            <Button loading={loading} type='primary' htmlType='submit' block disabled={isButtonDisabled} style={{ width: '20%', float: 'right' }}>
              Save
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal for Editing a Tag */}
      <Modal form={form} title='Update Tag' visible={isEditModalVisible} onCancel={handleEditCancel} footer={null} bodyStyle={{ paddingTop: 0, paddingBottom: 5 }}>
        <Form
          form={form}
          onFinish={handleSubmit}
          onValuesChange={handleValuesChangeEdit} // Track field changes
          layout='vertical'>
          <Form.Item
            label={
              <span>
                Tag Name <span style={{ color: 'red' }}> *</span>
              </span>
            }
            name='name'
            rules={[{ message: 'Please input the tag name.' }]}>
            <Input placeholder='Edit tag name.' />
          </Form.Item>

          <Form.Item label='Description' name='description' rules={[{ message: 'Please input the description.' }]}>
            <Input.TextArea style={{ height: '120px', overflowY: 'auto', resize: 'none' }} placeholder='Edit tag description.' />
          </Form.Item>
          <Form.Item>
            <Button loading={loading} type='primary' htmlType='submit' block disabled={isEditButtonDisabled} style={{ width: '20%', float: 'right' }}>
              Update
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* DELETE CONFIRMATION MODAL */}
      <Modal open={isDeleteModalOpen} onCancel={() => setIsDeleteModalOpen(false)} footer={null} title='Delete Confirmation'>
        <div style={{ marginBottom: 20 }}>
          Are you sure you want to delete&nbsp;
          <strong>'{deleteName}'</strong> tag?
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

export default Tags;
