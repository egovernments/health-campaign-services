import dayjs from 'dayjs';
import { Parser } from 'node-sql-parser';
import React, { useState, useEffect } from 'react';
import LoadingWidget from '../../components/LoadingWidget';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';
import ResizableReact from '../../components/ResizeableReact';
import { Table, Typography, Layout, Card, notification, Input, Modal, Dropdown, Menu, Button, Radio } from 'antd';
import { getAllHistory, getHistoryCount, updateResponseFeedback, addToExample, getConfigurationById } from '../../services/ProductApis';
import { HistoryOutlined, CloseCircleOutlined, LikeOutlined, DislikeOutlined, CheckCircleOutlined, LikeFilled, DislikeFilled, PlusSquareOutlined, MoreOutlined } from '@ant-design/icons';

const ChatHistory = () => {
  const [tableData, setTableData] = useState([]);
  const [pageSize, setPageSize] = useState(10);
  const [currentPage, setCurrentPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [currentLength, setCurrentLength] = useState(1);
  const [currentConfigId, setCurrentConfigId] = useState(null);
  const [dbType, setDbType] = useState(null);

  const [isModalVisible, setIsModalVisible] = useState(false);
  const [sqlExampleModalMode, setSqlExampleModalMode] = useState('edit');
  const [sqlExampleModalData, setsqlExampleModalData] = useState({
    question: '',
    query: '',
    exampleType: 'Semantic',
  });
  const [sqlError, setSqlError] = useState(null);

  const { Header } = Layout;
  const { Text } = Typography;

  const USERLEVEL = localStorage.getItem('roles');

  const [columns, setColumns] = useState(
    [
      {
        title: 'ID',
        dataIndex: 'id',
        key: 'id',
        width: 30,
        align: 'center',
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
      },
      {
        title: 'Dataset ID',
        dataIndex: 'configId',
        key: 'configId',
        width: 40,
        align: 'center',
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
      },
      USERLEVEL !== 'user' && {
        title: 'User ID',
        dataIndex: 'userId',
        key: 'userId',
        width: 40,
        align: 'center',
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
      },
      // USERLEVEL !== 'user' && {
      //   title: 'LLM ID',
      //   dataIndex: 'model_config_id',
      //   key: 'model_config_id',
      //   width: 60,
      //   align: 'center',
      //   onHeaderCell: (column) => ({
      //     width: column.width,
      //     onResize: handleResize(column),
      //   }),
      // },
      {
        title: 'Session ID',
        dataIndex: 'sessionId',
        key: 'sessionId',
        width: 80,
        align: 'center',
        ellipsis: true,
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
      },
      {
        title: <div style={{ textAlign: 'center' }}>User Question</div>,
        dataIndex: 'question',
        key: 'question',
        width: 200,
        ellipsis: true,
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
      },
      {
        title: 'Created At',
        dataIndex: 'created_at',
        key: 'created_at',
        width: 80,
        align: 'center',
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
        render: (text) => <Text>{dayjs(text).format('DD-MM-YYYY hh:mm A')}</Text>,
      },
      {
        title: "Feedback",
        key: "is_correct",
        width: 50,
        align: "center",
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
        render: (_, record) => {
          const { is_correct, id } = record;

          const handleIconClick = async (value) => {
            let newValue = value;

            if (record.is_correct === value) {
              newValue = null;
            }

            await handleFeedback(id, newValue);
            fetchData(currentPage, pageSize);
          };

          return (
            <div style={{ display: "flex", justifyContent: "center", gap: 10 }}>
              {is_correct === true ? (
                <LikeFilled
                  style={{ color: "#1890ff", cursor: "pointer" }}
                  onClick={() => handleIconClick(true)}
                />
              ) : (
                <LikeOutlined
                  style={{ cursor: "pointer" }}
                  onClick={() => handleIconClick(true)}
                />
              )}

              {is_correct === false ? (
                <DislikeFilled
                  style={{ color: "#ff4d4f", cursor: "pointer" }}
                  onClick={() => handleIconClick(false)}
                />
              ) : (
                <DislikeOutlined
                  style={{ cursor: "pointer" }}
                  onClick={() => handleIconClick(false)}
                />
              )}
            </div>
          );
        },
      },
      USERLEVEL !== 'user' && {
        title: 'Actions',
        key: 'actions',
        width: 50,
        align: 'center',
        onHeaderCell: (column) => ({
          width: column.width,
          onResize: handleResize(column),
        }),
        render: (_, record) => {
          const menu = (
            <Menu>
              <Menu.Item key='add' onClick={() => handleAddToExample(record)}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <PlusSquareOutlined
                    style={{
                      color: '#1890ff',
                      fontSize: '18px',
                      marginRight: 8,
                    }}
                  />
                  Add to Examples
                </div>
              </Menu.Item>
            </Menu>
          );

          return (
            <Dropdown overlay={menu} trigger={['click']}>
              <Button type='text' icon={<MoreOutlined style={{ fontSize: 18 }} />} />
            </Dropdown>
          );
        },
      },
    ].filter(Boolean)
  ); // <- Remove any false values (undefined columns)

  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };

  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  const NOT_ALLOWED_QUERIES = ['insert', 'update', 'delete', 'drop', 'alter', 'create', 'truncate', 'merge', 'rename'];

  const validateSQL = (sql) => {
    const parser = new Parser();
    const dialects = ['mysql', 'postgresql', 'transactsql'];
    let valid = false;

    // Normalize SQL to lowercase and check for disallowed queries
    const lowerSql = sql.toLowerCase();
    for (const keyword of NOT_ALLOWED_QUERIES) {
      const regex = new RegExp(`\\b${keyword}\\b`, 'i');
      if (regex.test(sql)) {
        setSqlError(`Use of "${keyword.toUpperCase()}" is not allowed`);
        return false;
      }
    }

    // Try parsing with each dialect
    for (const dialect of dialects) {
      try {
        parser.astify(sql, { database: dialect });
        valid = true;
        break;
      } catch (err) {
        continue;
      }
    }

    setSqlError(valid ? null : 'Invalid SQL query');
    return valid;
  };

  const fetchLength = async () => {
    try {
      const data_length = await getHistoryCount();
      setCurrentLength(data_length.data);
    } catch (error) {
      console.error('Error fetching history:', error?.response?.data?.error);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch history');
    }
  };

  const handleFeedback = async (id, feedback) => {
    try {
      const data = { response_status: feedback };
      await updateResponseFeedback(data, id);
    } catch (error) {
      console.error('Error updating feedback error:', error);
    }
  };

  const handleAddToExample = async (record) => {
    setSqlExampleModalMode('edit');
    setsqlExampleModalData({
      question: record.question || '',
      query: record.response || '',
      exampleType: 'Semantic',
    });
    setCurrentConfigId(record.configId);
    setSqlError(null);
    setIsModalVisible(true);
    try {
      const configRes = await getConfigurationById(record.configId);
      setDbType(JSON.parse(configRes.data.data).db_type);
    } catch (err) {
      console.error('Error fetching config:', err);
      setDbType(null);
    }
  };

  const fetchData = async (page, perPage) => {
    try {
      setLoading(true);
      const response = await getAllHistory(page, perPage);
      const data = Array.isArray(response.data) ? response.data : [];

      // Transform the data for the table
      const formattedData = data.map((item) => ({
        key: item.id,
        id: item.id,
        configId: item.config_id,
        userId: item.user_id,
        sessionId: item.session_id,
        question: item.message.user_question,
        response: item.message.sql_query,
        model_config_id: item.model_config_id,
        created_at: item.created_at,
        is_correct: item.is_correct,
        tokenDetails: JSON.parse(item.token_details),
      }));

      setTableData(formattedData);
    } catch (error) {
      console.error('Error fetching data:', error?.response?.data?.error);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData(currentPage, pageSize);
    fetchLength();
  }, []); // Fetch data when page or pageSize changes

  // Keep the pagination handlers
  const handlePaginationChange = (page, pageSize) => {
    setCurrentPage(page);
    setPageSize(pageSize);
    fetchData(page, pageSize);
  };

  const handleShowSizeChange = (current, size) => {
    setPageSize(size);
    fetchData(current, size);
  };

  const expandedRowRender = (record) => {
    const sentence1 = (
      <div>
        <strong>Question:</strong>
        <span style={{ fontWeight: '500', color: '#1677ff' }}>{record.question ?? 'NA'}</span>
      </div>
    );

    const sentence2 = (
      <div>
        <strong>Query:</strong>
        <span style={{ fontWeight: '500', color: '#1677ff' }}>{record.response ?? 'NA'}</span>
      </div>
    );

    const tokenDetails = record.tokenDetails;

    return (
      <div>
        {sentence1}
        {sentence2}
        <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
          <div style={{ display: 'flex', flexDirection: 'row' }}>
            <strong>Input Tokens:</strong>
            <div style={{ marginLeft: '5px' }}>{tokenDetails.input_tokens ?? 'NA'}</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'row' }}>
            <strong>Output Tokens:</strong>
            <div style={{ marginLeft: '5px' }}>{tokenDetails.output_tokens ?? 'NA'}</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'row' }}>
            <strong>Total Tokens:</strong>
            <div style={{ marginLeft: '5px' }}>{tokenDetails.total_tokens ?? 'NA'}</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'row' }}>
            <strong>Cost:</strong>
            <div style={{ marginLeft: '5px' }}>{typeof tokenDetails.total_cost_USD === 'number' ? `$${tokenDetails.total_cost_USD.toFixed(4)}` : 'NA'}</div>
          </div>
        </div>
      </div>
    );
  };

  const handleSqlAdd = async () => {
    const { question, query } = sqlExampleModalData;

    // only validate if db is NOT Elasticsearch
    if (dbType !== 'Elasticsearch' && !validateSQL(query)) return;

    const payload = {
      key: question.trim(),
      value: query.trim(),
      type: sqlExampleModalData.exampleType || 'Semantic',
    };

    try {
      await addToExample(currentConfigId, payload);
      showNotification(<CheckCircleOutlined style={{ color: '#52c41a' }} />, 'Success', 'Example added successfully.');
      setIsModalVisible(false);
    } catch (error) {
      console.error('Error adding example:', error);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to add example');
    }
  };

  const handleResize =
    (column) =>
    (e, { size }) => {
      const MIN_WIDTH = 30;
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
      <Layout>
        <PageBreadcrumb title='Chat History' />

        <Header className='sticky-header'>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              width: '100%',
            }}>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <HistoryOutlined style={{ fontSize: '20px' }} />
              <span
                style={{
                  fontSize: '20px',
                  color: 'black',
                  marginLeft: '10px',
                  fontWeight: 'bold',
                }}>
                Chat History
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
                columns={columns}
                dataSource={tableData}
                rowKey='id'
                components={{ header: { cell: ResizableReact } }}
                pagination={{
                  current: currentPage,
                  pageSize: pageSize,
                  total: currentLength,
                  showSizeChanger: true,
                  onChange: handlePaginationChange,
                  onShowSizeChange: handleShowSizeChange,
                }}
                size='small'
                expandable={{
                  expandedRowRender,
                  rowExpandable: (record) => true,
                }}
                sticky
                scroll={{ y: '50vh' }}
                style={{ height: 380 }}
              />
            </Card>
          </main>
        )}
      </Layout>

      <Modal
        title='Add Example'
        open={isModalVisible}
        onCancel={() => {
          setIsModalVisible(false);
          setSqlError(null);
        }}
        footer={null}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            marginTop: '-20px',
          }}>
          <label>
            Example Type
            <div style={{ marginTop: 6 }}>
              <Radio.Group
                value={sqlExampleModalData.exampleType}
                onChange={(e) =>
                  setsqlExampleModalData({
                    ...sqlExampleModalData,
                    exampleType: e.target.value,
                  })
                }>
                <Radio value='Semantic'>Semantic Search</Radio>
                <Radio value='Core'>Core</Radio>
              </Radio.Group>
            </div>
          </label>
          <label>
            Question
            <Input.TextArea
              rows={3}
              value={sqlExampleModalData.question}
              onChange={(e) =>
                setsqlExampleModalData({
                  ...sqlExampleModalData,
                  question: e.target.value,
                })
              }
              placeholder='Enter the question'
              style={{ resize: 'none', marginTop: '2px' }}
              readOnly={sqlExampleModalMode === 'view'} // always false
            />
          </label>
          <label>
            Query
            <Input.TextArea
              rows={6}
              value={sqlExampleModalData.query}
              onChange={(e) => {
                const query = e.target.value;
                setsqlExampleModalData({
                  ...sqlExampleModalData,
                  query,
                });

                if (dbType !== 'Elasticsearch') {
                  validateSQL(query);
                } else {
                  setSqlError(null);
                }
              }}
              placeholder='Enter the query'
              style={{ resize: 'none', marginTop: '2px' }}
              readOnly={sqlExampleModalMode === 'view'}
            />
            <div
              style={{
                height: '20px',
                color: sqlError ? 'red' : 'transparent',
                marginTop: '4px',
                transition: 'color 0.3s ease',
                display: 'flex',
                alignItems: 'center',
              }}>
              <CloseCircleOutlined
                style={{
                  marginRight: 4,
                  visibility: sqlError ? 'visible' : 'hidden',
                }}
              />
              {sqlError}
            </div>
          </label>
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
              marginTop: -12,
            }}>
            <Button onClick={() => setIsModalVisible(false)}>Cancel</Button>
            <Button type='primary' onClick={handleSqlAdd} disabled={!sqlExampleModalData.question.trim() || !sqlExampleModalData.query.trim() || !!sqlError}>
              OK
            </Button>
          </div>
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

export default ChatHistory;
