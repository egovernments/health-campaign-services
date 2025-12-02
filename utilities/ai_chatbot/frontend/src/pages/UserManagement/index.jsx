import dayjs from 'dayjs';
import React, { useEffect, useState } from 'react';
import ResizableReact from '../../components/ResizeableReact';
import LoadingWidget from '../../components/LoadingWidget';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';
import { UserOutlined, EditFilled, MoreOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Button, Card, Layout, Form, Select, notification, Modal, Table, Tag, Dropdown, Space, Typography, Tooltip } from 'antd';
import './style.scss';

// Import services & helpers
import { getAllTags, getAllUsers, updateUserById, getUserById } from '../../services/ProductApis';

const UserManagement = () => {
  const { Header } = Layout;
  const { Option } = Select;
  const { Text } = Typography;

  // Separate form instances for each modal
  const [editForm] = Form.useForm();

  // Default UseStates
  const [tags, setTags] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);
  const [pageSize, setpageSize] = useState(10);

  // UseStates For Update Functionality
  const [isActive, setIsActive] = useState(false);
  const [selectedTags, setSelectedTags] = useState([]);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [isEditButtonDisabled, setEditIsButtonDisabled] = useState(false);

  const [columns, setColumns] = useState([
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      fixed: 'top',
      width: 40,
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: <div style={{ textAlign: 'center' }}>Username</div>,
      dataIndex: 'user_name',
      key: 'user_name',
      fixed: 'top',
      width: 100,
      ellipsis: true,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'GUID',
      dataIndex: 'guid',
      key: 'guid',
      fixed: 'top',
      width: 120,
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      ellipsis: { showTitle: false },
      render: (guid) => <Tooltip title={guid}>{guid}</Tooltip>,
    },
    {
      title: 'Tags',
      dataIndex: 'tag_names',
      key: 'tag_names',
      width: 50,
      fixed: 'top',
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (tagNames) => {
        if (Array.isArray(tagNames) && tagNames.length > 0) {
          return (
            <>
              {tagNames.map((tag, index) => {
                const maxLength = 8;
                const isLong = tag.length > maxLength;
                const displayTag = isLong ? `${tag.slice(0, maxLength)}...` : tag;

                return (
                  <Tooltip key={tag} title={isLong ? tag : ''}>
                    <Tag
                      color={getColorByIndex(index)}
                      style={{
                        margin: '4px 4px 4px 0',
                        cursor: isLong ? 'pointer' : 'default',
                      }}>
                      {displayTag}
                    </Tag>
                  </Tooltip>
                );
              })}
            </>
          );
        }
      },
    },
    {
      title: 'Status',
      dataIndex: 'is_active',
      key: 'is_active',
      fixed: 'top',
      width: 50,
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text, record) => <Tag color={text ? 'green' : 'red'}>{text ? 'Active' : 'Inactive'}</Tag>,
    },
    {
      title: 'Created At',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 80,
      fixed: 'top',
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text) => {
        const formattedDate = dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : '';
        return <Text>{formattedDate}</Text>;
      },
    },
    {
      title: 'Updated At',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 80,
      fixed: 'top',
      align: 'center',
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (text) => {
        const formattedDate = dayjs(text).isValid() ? dayjs(text).format('DD-MM-YYYY hh:mm A') : '';
        return <Text>{formattedDate}</Text>;
      },
    },
    {
      key: 'actions',
      align: 'center',
      width: 40,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      title: <div style={{ justifyContent: 'center' }}>Actions</div>,
      render: (record) => {
        return record?.user_name === 'vadmin' ? null : <ActionList record={record} style={{ justifyContent: 'center' }} />;
      },
      fixed: 'right',
    },
  ]);

  // Props for Notification
  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };
  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  // Colors for Tags
  const colors = ['purple', 'blue', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'red', 'geekblue', 'magenta'];
  const getColorByIndex = (index) => colors[index % colors.length];

  // For getting values while editing
  useEffect(() => {
    if (currentRecord && isEditModalVisible) {
      const tagsArray = Array.isArray(currentRecord.tag_ids) ? currentRecord.tag_ids : currentRecord.tag_ids ? currentRecord.tag_ids.split(',') : [];

      editForm.setFieldsValue({
        user_name: currentRecord.user_name,
        first_name: currentRecord.first_name,
        last_name: currentRecord.last_name,
        email: currentRecord.email,
        role: currentRecord.role,
        tags: tagsArray,
      });

      setSelectedTags(tagsArray);
      setTimeout(() => {
        setIsActive(currentRecord.is_active);
      }, 1000);
    }
  }, [isEditModalVisible, currentRecord, editForm]);

  useEffect(() => {
    fetchTags();
    fetchUsers();
  }, []);

  const handleEditCancel = () => {
    setIsEditModalVisible(false);
    setCurrentRecord(null);
    editForm.resetFields();
  };

  const showEditModal = async (record) => {
    setIsEditModalVisible(true);
    setCurrentRecord(null);

    try {
      const response = await getUserById(record.id);
      const data = response.data;

      // Parse tag details from response and add them to data
      const tagNames = data.tags.map((tag) => tag.name);
      const tagIds = data.tags.map((tag) => tag.id);

      setCurrentRecord({
        ...data,
        tag_name: tagNames,
        tag_ids: tagIds,
        id: record.id,
      });
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', 'Failed to fetch tag details.');
    }
  };

  const fetchTags = async () => {
    try {
      const data = await getAllTags();
      setTags(data.data);
    } catch (error) {
      console.error('Error fetching tags:', error?.response?.data?.error);
    }
  };

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const data = await getAllUsers();
      const processedUsers = data.data.map((user) => {
        const tagNames = user.tags ? user.tags.map((tag) => tag.name) : [];

        return { ...user, tag_names: tagNames };
      });

      setUsers(processedUsers);
    } catch (error) {
      console.error('Error fetching users:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = () => {
    setLoading(true);
    editForm
      .validateFields()
      .then((values) => {
        const formData = {
          tag_ids: selectedTags.join(',') || '',
          user_name: localStorage.getItem('user_name')
        };
        updateUserById(currentRecord.id, formData)
          .then((response) => {
            setLoading(false);
            setIsEditModalVisible(false);
            fetchUsers();
            notification.success({
              message: 'Success',
              description: 'User updated successfully.',
              style: { width: 350 },
            });
          })
          .catch((error) => {
            console.error(error);
            notification.error({
              message: 'Error',
              description: error?.response?.data?.error,
              style: { width: 350 },
            });
            setLoading(false);
          });
      })
      .catch((errorInfo) => {
        setLoading(false);
        console.error('Validation Failed:', errorInfo);
      });
  };

  // This is the actionlist defined for the 3-dots option of each configuration
  const ActionList = ({ record }) => {
    const items = [
      {
        key: 'update',
        label: (
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <EditFilled style={{ marginRight: '8px' }} />
            Update Tags
          </div>
        ),
        onClick: () => showEditModal(record),
      },
    ];

    return (
      <main>
        <Dropdown menu={{ items }} trigger={['click']}>
          <MoreOutlined
            style={{
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
            }}
          />
        </Dropdown>
      </main>
    );
  };

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
      <PageBreadcrumb title='User Management' />
      <Header className='sticky-header'>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
          }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <UserOutlined style={{ fontSize: '20px', color: 'black' }} />
            <span
              style={{
                fontSize: '20px',
                color: 'black',
                marginLeft: '10px',
                fontWeight: 'bold',
              }}>
              User Management
            </span>
          </div>
        </div>
      </Header>
      {loading ? (
        <LoadingWidget />
      ) : (
        <main className='main-container'>
          <Card bordered={false} className='table-card'>
            <Table
              components={{ header: { cell: ResizableReact } }}
              columns={columns}
              dataSource={users}
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

      {/* Edit User Modal */}
      <Modal
        title='Update Tags'
        visible={isEditModalVisible}
        onCancel={handleEditCancel}
        footer={null}
        style={{ top: '15%', transition: 'none' }}
        afterClose={() => editForm.resetFields()}
        width={500}>
        <Form layout='vertical' style={{ display: 'flex', flexDirection: 'column', height: '100%' }} onFinish={handleUpdate} form={editForm}>
          <Form.Item style={{ marginBottom: 12 }} label={<span>Tags</span>} name='tags'>
            <Select
              mode='multiple'
              placeholder='Select tags'
              allowClear
              value={selectedTags}
              onChange={(value) => setSelectedTags(value)}
              showSearch
              optionFilterProp='children'
              filterOption={(input, option) => option?.children?.toLowerCase().includes(input.toLowerCase())}>
              {tags &&
                tags.map((tag) => (
                  <Option key={tag.id} value={tag.id}>
                    {tag.name}
                  </Option>
                ))}
            </Select>
          </Form.Item>

          {/* Save Button Positioned at Bottom Right */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              marginTop: '8px',
            }}>
            <Space direction='horizontal' size={10}>
              <Button
                type='primary'
                disabled={isEditButtonDisabled}
                onClick={() => {
                  editForm.submit();
                  setTimeout(() => {
                    setIsCreateModalVisible(false);
                  }, 0);
                }}>
                Update
              </Button>
            </Space>
          </div>
        </Form>
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

export default UserManagement;
