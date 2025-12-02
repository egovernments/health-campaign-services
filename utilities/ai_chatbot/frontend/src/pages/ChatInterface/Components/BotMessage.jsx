import '../styles.scss';
import React, { useState, useEffect } from 'react';
import TypingEffect from './TypingEffect';
import ReactECharts from 'echarts-for-react';
import ClipboardJS from 'clipboard';
import { updateResponseFeedback } from '../../../services/ProductApis';
import { EllipsisOutlined, UploadOutlined, LikeOutlined, DislikeOutlined, EyeOutlined, CopyOutlined, CheckOutlined } from '@ant-design/icons';
import { Input, Button, Row, Col, Avatar, Space, Collapse, Typography, Table, Tabs, Dropdown, Modal, ConfigProvider } from 'antd';
import { exportToCSV, formatText, highlightText, getAreaChartOptions, getBarChartOptions, getLineChartOptions, toTitleCase, formatDateValues, checkIfGraphData } from '../utils';

const { TabPane } = Tabs;
const { Text } = Typography;
const { Panel } = Collapse;

const BotMessage = ({ message, setIsInputDisabled, useTypingEffect, chatContainerRef, size }) => {
  const [isTypingComplete, setIsTypingComplete] = useState(false);
  const [feedback, setFeedback] = useState(message.feedback !== undefined ? message.feedback : null);
  const [feedbackGiven, setFeedbackGiven] = useState(message.feedback !== undefined && message.feedback !== null);
  const [searchTerm, setSearchTerm] = useState('');
  const [isQueryModalVisible, setIsQueryModalVisible] = useState(false);
  const [copied, setCopied] = useState(false);

  const srch_hgt = 36;

  useEffect(() => {
    if (message.fromHistory) setIsTypingComplete(true);
  }, [message.fromHistory]);

  useEffect(() => {
    if (copied) {
      const timer = setTimeout(() => {
        setCopied(false);
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [copied]);

  const handleFeedback = async (feedbackValue) => {
    try {
      const data = { response_status: feedbackValue };
      await updateResponseFeedback(data, message.chat_id);
      setFeedback(feedbackValue);
      setFeedbackGiven(true);
    } catch (error) {
      console.error('Error updating feedback:', error);
    }
  };

  const handleTypingComplete = () => {
    setIsTypingComplete(true);
    setIsInputDisabled(false);
  };

  const handleCopyQuery = (e) => {
    e.stopPropagation();
    const textToCopy = message.query || '';

    const tempButton = document.createElement('button');
    document.body.appendChild(tempButton);

    const clipboard = new ClipboardJS(tempButton, {
      text: () => textToCopy,
    });

    clipboard.on('success', () => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
      clipboard.destroy();
      document.body.removeChild(tempButton);
    });

    clipboard.on('error', () => {
      try {
        const input = document.createElement('input');
        input.value = textToCopy;
        document.body.appendChild(input);
        input.focus();
        input.select();
        const success = document.execCommand('copy');
        document.body.removeChild(input);

        if (success) {
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        } else {
          window.prompt('Copy this text:', textToCopy);
        }
      } catch (err) {
        console.error('Clipboard fallback failed:', err);
        window.prompt('Copy this text:', textToCopy);
      }

      clipboard.destroy();
      document.body.removeChild(tempButton);
    });

    tempButton.click();
  };

  const columns = message.data && message.data.length > 0 ? Object.keys(message.data[0]) : [];
  const numRows = message.data ? message.data.length : 0;
  const numCols = message.data && message.data.length > 0 ? Object.keys(message.data[0]).length : 0;
  const isGraphData = checkIfGraphData(message.data);
  const isTableData = numRows > 1;
  const isCollapseVisible = numCols > 1 && numRows > 1;

  const menu = {
    items: [
      {
        key: 'export',
        label: 'Export as CSV',
        icon: <UploadOutlined />,
        onClick: () => exportToCSV(message.data),
      },
    ],
  };

  const getConditionalColumns = (data, searchTerm, maxVisible = 4) => {
    if (!data || data.length === 0) return [];
    const allKeys = Object.keys(data[0]);
    const createColumn = (key) => ({
      title: toTitleCase(key),
      dataIndex: key,
      key,
      ellipsis: true,
      render: (text) => (
        <div style={{ maxWidth: 150, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={text}>
          {highlightText(text, searchTerm)}
        </div>
      ),
    });
    if (size !== '50%') return allKeys.map(createColumn);
    const visibleKeys = allKeys.slice(0, maxVisible);
    const baseColumns = visibleKeys.map(createColumn);
    if (allKeys.length > maxVisible) {
      baseColumns.push({ title: '...', key: 'ellipsis', render: () => <span>...</span> });
    }
    return baseColumns;
  };

  return (
    <Row style={{ marginBottom: '10px' }} justify='start'>
      <Col span={24}>
        <Space align='start'>
          <Avatar src='https://vidaimages.s3.ap-south-1.amazonaws.com/kouventa.svg' style={{ marginTop: '5px' }} />
          <div
            style={{
              background: 'transparent',
              color: '#000',
              padding: '10px 15px 0 15px',
              maxWidth: '100%',
              wordBreak: 'break-word',
              whiteSpace: 'pre-wrap',
            }}>
            {useTypingEffect && !message.fromHistory ? (
              <TypingEffect text={message.text} onTypingComplete={handleTypingComplete} scrollContainerRef={chatContainerRef} />
            ) : (
              <Text>{formatText(message.text)}</Text>
            )}
          </div>
        </Space>

        {isTypingComplete && isCollapseVisible && Array.isArray(message.data) && message.data.length > 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', marginTop: '10px' }}>
            <Collapse className='custom-collapse' style={{ width: '93%', borderRadius: '10px', overflow: 'hidden', marginLeft: '12px', backgroundColor: '#F5F5F5' }} size='small' bordered={false}>
              <Panel header={<span>More Details</span>} key='1' size='small'>
                <Tabs
                  destroyInactiveTabPane
                  defaultActiveKey='1'
                  tabBarExtraContent={
                    <Dropdown menu={menu} trigger={['click']}>
                      <Button icon={<EllipsisOutlined />} style={{ width: 30, height: 30, marginBottom: 10, borderRadius: '20%', display: 'flex', justifyContent: 'center', alignItems: 'center' }} />
                    </Dropdown>
                  }>
                  {isTableData && (
                    <TabPane tab='Table' key='1'>
                      <Input.Search placeholder='Search in table...' onChange={(e) => setSearchTerm(e.target.value)} />
                      <div style={{ maxWidth: '100%', overflowX: 'auto', overflowY: 'max-content', whiteSpace: 'nowrap', paddingBottom: '8px', boxSizing: 'border-box' }}>
                        <Table
                          dataSource={formatDateValues(message.data)}
                          style={{ minHeight: 235, overflowX: 'auto' }}
                          pagination={{ pageSize: 5 }}
                          columns={getConditionalColumns(formatDateValues(message.data), searchTerm)}
                          size='small'
                          id='small-table'
                          scroll={{ x: 'max-content', y: 300 }}
                          bordered
                        />
                      </div>
                    </TabPane>
                  )}
                  {isGraphData && (
                    <>
                      <TabPane tab='Bar Chart' key='2'>
                        <ReactECharts option={getBarChartOptions(message.data)} style={{ height: `${235 + srch_hgt}px`, width: '100%', paddingLeft: '20px' }} />
                      </TabPane>
                      <TabPane tab='Line Chart' key='3'>
                        <ReactECharts option={getLineChartOptions(message.data)} style={{ height: `${235 + srch_hgt}px`, width: '100%', paddingLeft: '20px' }} />
                      </TabPane>
                      <TabPane tab='Area Chart' key='4'>
                        <ReactECharts option={getAreaChartOptions(message.data)} style={{ height: `${235 + srch_hgt}px`, width: '100%', paddingLeft: '20px' }} />
                      </TabPane>
                    </>
                  )}
                </Tabs>
              </Panel>
            </Collapse>
          </div>
        )}

        {(isTypingComplete || message.fromHistory) && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-start', marginLeft: '60px', gap: '8px', marginTop: '10px'}}>
            <div style={{ opacity: feedbackGiven ? 0.6 : 1, display: 'flex', gap: '8px' }}>
            <Button
              shape='circle'
              icon={<LikeOutlined />}
              size='small'
              onClick={() => handleFeedback(true)}
              style={{ backgroundColor: feedback === true ? 'blue' : undefined, color: feedback === true ? '#fff' : undefined }}
            />
            <Button
              shape='circle'
              icon={<DislikeOutlined />}
              size='small'
              onClick={() => handleFeedback(false)}
              style={{ backgroundColor: feedback === false ? 'red' : undefined, color: feedback === false ? '#fff' : undefined }}
            />
            </div>
            <>
              <Text type='secondary' style={{ margin: '0 8px' }}>
                |
              </Text>
              <ConfigProvider
                theme={{
                  token: {
                    colorPrimary: 'black',
                    colorPrimaryHover: 'black',
                    colorPrimaryActive: 'black',
                  },
                }}>
                <Button
                  icon={<EyeOutlined style={{ verticalAlign: 'middle' }} />}
                  size='small'
                  onClick={() => {
                    setIsQueryModalVisible(true);
                    if (document.activeElement instanceof HTMLElement) {
                      document.activeElement.blur();
                    }
                  }}
                  style={{
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    padding: '15px',
                  }}>
                  Show Query
                </Button>
              </ConfigProvider>

              <Modal
                title={
                  <div style={{ display: 'flex', justifyContent: 'start', alignItems: 'center' }}>
                    <span>Generated Query</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                      <Button type='text' icon={copied ? <CheckOutlined style={{ color: '#52c41a' }} /> : <CopyOutlined />} onClick={handleCopyQuery} />
                      {copied && <span style={{ color: '#52c41a', fontWeight: 500 }}>Text copied.</span>}
                    </div>
                  </div>
                }
                open={isQueryModalVisible}
                onCancel={() => setIsQueryModalVisible(false)}
                width={700}
                bodyStyle={{
                  maxHeight: '250px',
                  overflowY: 'auto',
                  backgroundColor: 'white',
                  padding: '16px',
                  borderRadius: '15px',
                  whiteSpace: 'pre-wrap',
                  fontFamily: 'monospace',
                }}
                footer={[
                  <Button key='ok' type='primary' onClick={() => setIsQueryModalVisible(false)} style={{ borderRadius: '8px' }}>
                    OK
                  </Button>,
                ]}>
                <pre style={{ margin: 0 }}>{message.query ? message.query : 'No query generated.'}</pre>
              </Modal>
            </>
          </div>
        )}
      </Col>
    </Row>
  );
};

export default BotMessage;
