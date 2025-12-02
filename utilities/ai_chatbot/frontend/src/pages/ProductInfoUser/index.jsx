import dayjs from 'dayjs';
import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import React, { useState, useEffect } from 'react';
import { DatabaseOutlined } from '@ant-design/icons';
import { Card, Layout, Typography, Space, Button, Tag, Table, Tooltip } from 'antd';
import { getAllConfigurations } from '../../services/ProductApis';

// Import custom styling
import './style.scss';

// Import Local Components
import launchChatIcon from '../../assets/imgs/chat-icon.png';
import LoadingWidget from '../../components/LoadingWidget';
import ResizableReact from '../../components/ResizeableReact';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';

// Import services & helpers
import config from '../../config';
import { getDefaultModel } from '../../services/ProductApis';

const ProductInfo = () => {
  // UseStates
  const [tableData, setTableData] = useState([]);
  const [pageSize, setpageSize] = useState(10);
  const [loading, setLoading] = useState(false);

  // Refractoring Imported Components
  const { Header } = Layout;
  const { Text } = Typography;

  // Local Storage Variables
  const token = localStorage.getItem('access_token');

  const [columns, setColumns] = useState([
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 30,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      align: 'center',
    },
    {
      title: <div style={{ textAlign: 'center' }}>Name</div>,
      dataIndex: 'name',
      key: 'name',
      width: 50,
      ellipsis: true,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
    },
    {
      title: 'Database Type',
      dataIndex: 'db_type',
      key: 'db_type',
      width: 40,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      align: 'center',
    },
    {
      title: <div style={{ textAlign: 'center' }}>Table(s) / Index</div>,
      dataIndex: 'table_name',
      key: 'table_name',
      width: 50,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      ellipsis: true,
      render: (text) => {
        if (text == '*') return <span>All Tables</span>;
        return text;
      },
    },
    {
      title: 'Tags',
      dataIndex: 'tag_name',
      key: 'tag_name',
      width: 30,
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
      width: 30,
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
      width: 50,
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
      width: 50,
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
      key: 'launch',
      align: 'center',
      width: 40,
      title: <div style={{ justifyContent: 'center' }}>Launch Chatbot</div>,
      onHeaderCell: (column) => ({ width: column.width, onResize: handleResize(column) }),
      render: (record) => (
        <Space>
          {record.is_active ? (
            <Button
              type='link'
              onClick={() => {
                fetchDefaultModel();
                window.open(`/chat?dsname=${record.name}&dsId=${record.id}&sessionId=${uuidv4()}`, '_blank');
              }}
              style={{ padding: 0 }}>
              <img src={launchChatIcon} alt='Launch' style={{ width: 24, height: 24 }} />
            </Button>
          ) : (
            <Button type='link' disabled style={{ padding: 0 }}>
              <img src={launchChatIcon} alt='Disabled' style={{ width: 24, height: 24, opacity: 0.3 }} />
            </Button>
          )}
        </Space>
      ),
      fixed: 'right',
    },
  ]);

  const fetchDefaultModel = async () => {
    try {
      const response = await getDefaultModel();
      if (response) {
        localStorage.setItem('defaultModel', response.data.name);
      } else {
        console.error('Failed to fetch default model');
      }
    } catch (error) {
      console.error('Error fetching default model:', error);
    }
  };

  // Handler to fetch the table
  const fetchTableData = () => {
    setLoading(true);

    getAllConfigurations()
      .then((res) => {
        setLoading(false);

        const parsedData = res.data.map((item) => {
          const parsedItem = JSON.parse(item.data);
          const parsedTag = item.tags || [];

          const tagNames = parsedTag.map((tag) => tag.name);
          const tagIds = parsedTag.map((tag) => tag.id);

          return {
            ...item,
            db_type: parsedItem.db_type,
            table_name: parsedItem.table_name,
            tag_name: tagNames,
            tag_ids: tagIds,
          };
        });

        setTableData(parsedData);
      })
      .catch((error) => {
        setLoading(false);
        console.error(error);
      });
  };

  // Colors for Tags
  const colors = ['purple', 'blue', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'red', 'geekblue', 'magenta'];
  const getColorByIndex = (index) => colors[index % colors.length];

  // Run the handler when the page is opened
  useEffect(() => {
    fetchTableData();
  }, []);

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
      <PageBreadcrumb title='Bot Configurations' />
      <Header className='sticky-header'>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            width: '100%',
          }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <DatabaseOutlined style={{ fontSize: '20px' }} />
            <span style={{ fontSize: '20px', color: 'black', marginLeft: '10px', fontWeight: 'bold' }}>Bot Configurations</span>
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
              components={{
                header: {
                  cell: ResizableReact,
                },
              }}
              dataSource={tableData}
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

export default ProductInfo;
