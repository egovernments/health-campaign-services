import './styles.scss';
import config from '../../config';
import { useLocation } from 'react-router-dom';
import UserMessage from './Components/UserMessage';
import BotMessage from './Components/BotMessage';
import SplitPane, { Pane } from 'react18-split-pane';
import React, { useState, useEffect, useRef } from 'react';
import { embedDashboard } from '@superset-ui/embedded-sdk';
import { getGuestToken, isDashboard } from '../../services/SupersetApis';
import { showNotification } from '../../utils/notification/notifications';
import { Layout, Input, Button, Row, Col, Avatar, Space, Typography, Tabs, Menu, Dropdown, Modal, Form, Tooltip, ConfigProvider } from 'antd';
import { getChatResponse, getChatHistory, getAllFavorites, deleteFavoriteById, updateFavoriteById, getDashboardsByConfiguration, getConfigurationById } from '../../services/ProductApis';
import { SendOutlined, CloseCircleOutlined, CheckCircleOutlined, InfoCircleOutlined, EditFilled, DeleteFilled, VerticalAlignBottomOutlined, QuestionCircleOutlined } from '@ant-design/icons';

const { Footer, Header } = Layout;
const { Text } = Typography;

const supersetUrl = config.supersetUrl;

// White theme configuration
const whiteTheme = {
  components: {
    Layout: {
      colorBgHeader: '#ffffff',
      colorBgBody: '#ffffff',
      colorBgContainer: '#ffffff',
    },
  },
};

// ChatInterface Component
const ChatInterface = () => {
  const location = useLocation();
  const inputRef = useRef(null);
  const chatContainerRef = useRef(null); // Reference for the chat container
  const maintoken = localStorage.getItem('chat_token');
  const appVersion = localStorage.getItem('appVersion');
  const defaultModel = localStorage.getItem('defaultModel');

  const params = new URLSearchParams(location.search);

  const dsId = params.get('dsId');
  const dsName = params.get('dsname');
  const sessionId = params.get('sessionId');

  const [size, setSize] = useState('0%');
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [favorites, setFavorites] = useState([]);
  const [isThinking, setIsThinking] = useState(false);
  const [isMaximized, setIsMaximized] = useState(false);
  const [isInputDisabled, setIsInputDisabled] = useState();
  const [embeddDashboards, setembeddDashboards] = useState([]);
  const [favoritesReload, setFavoritesReload] = useState(false);
  const [selectedDashboard, setSelectedDashboard] = useState(null);
  const [sampleQuestions, setSampleQuestions] = useState([]);

  // States for edit favorite modal
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [selectedFavorite, setSelectedFavorite] = useState(null);
  const [isEditButtonDisabled, setIsEditButtonDisabled] = useState(false);
  const [formEditFavorite] = Form.useForm();
  const [IsDashboard, setIsDashboard] = useState(false);
  const [dashboardLength, setDashboardLength] = useState(0);

  const handleCollapse = () => {
    setSize(size === '0%' ? '50%' : '0%');
  };
  const handleCollapseLeft = () => {
    if (isMaximized) {
      setSize('50%'); // Set to default size when collapsed
    } else {
      setSize('100%'); // Maximize the left pane
    }
    setIsMaximized(!isMaximized); // Toggle the maximized state
  };

  // Fetch sample questions from API
  useEffect(() => {
    const fetchSampleQuestions = async () => {
      if (dsId) {
        try {
          const response = await getConfigurationById(dsId);
          const questions = response.data.questions || [];
          // Extract the detail from each question object
          const questionDetails = questions.map((q) => q.detail);
          setSampleQuestions(questionDetails);
        } catch (error) {
          console.error('Error fetching sample questions:', error);
        }
      }
    };

    fetchSampleQuestions();
  }, [dsId]);

  useEffect(() => {
    const fetchChatHistory = async () => {
      try {
        const response = await getChatHistory(sessionId);
        const history = response?.data || [];
        const formattedMessages = history.flatMap((msg) => {
          const messages = [];

          // Human message
          if (msg?.message?.user_question) {
            messages.push({
              chat_id: msg.id,
              sender: 'human',
              text: msg.message.user_question,
              fromHistory: true,
            });
          }

          // AI message
          if (msg?.message) {
            let aiText = '';
            let aiData = null;

            try {
              const parsed = JSON.parse(msg.message.ai_response) || '';
              aiText = parsed?.text || msg.message.ai_response || 'No text generated.';
              aiData = parsed?.data || null;
            } catch (e) {
              aiText = msg.message.ai_response; // fallback to raw string
            }

            messages.push({
              feedback: msg.is_correct,
              chat_id: msg.id,
              sender: 'ai',
              text: aiText,
              query: msg.message.sql_query,
              fromHistory: true,
              data: aiData,
            });
          }
          return messages;
        });
        setMessages(formattedMessages);
      } catch (error) {
        console.error('Error fetching chat history:', error?.response?.data?.error || error);
      }
    };
    fetchChatHistory();
  }, [sessionId]);

  // Fetch favorites from API on component mount
  useEffect(() => {
    const guid = localStorage.getItem('guid') || '';
    const fetchFavorites = async () => {
      try {
        const response = await getAllFavorites(guid);
        setFavorites(response.data);
      } catch (error) {
        console.error('Error fetching favorites:', error?.response?.data?.error);
        showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
      }
    };
    fetchFavorites();
  }, [favoritesReload]);

  useEffect(() => {
    if (chatContainerRef.current) {
      // Add a small delay to ensure the DOM updates
      setTimeout(() => {
        chatContainerRef?.current?.scrollTo({
          top: chatContainerRef.current.scrollHeight,
          behavior: 'smooth',
        });
      }, 50); // Adjust the delay if needed
    }
  }, [messages]); // Triggered when messages array changes
  useEffect(() => {
    AvailableDashboard();
  }, []);

  const AvailableDashboard = async () => {
    try {
      const response = await isDashboard();
      setIsDashboard(response.data);
      const fetchAllEmbeddDashboards = async () => {
        try {
          const response = await getDashboardsByConfiguration(dsId);
          setembeddDashboards(response.data);
          if (response?.data?.length > 0) {
            setDashboardLength(response.data.length);
            setSelectedDashboard(response.data[0]);
          }
        } catch (error) {
          console.error('Error fetching dashboards:', error);
        }
      };
      if (response.data === true) {
        fetchAllEmbeddDashboards();
        handleTabChange();
      }
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    const chatToken = localStorage.getItem('chat_token');
    const mountPoint = document.getElementById('superset-container');

    if (chatToken && selectedDashboard) {
      const fetchGuestToken = async () => {
        try {
          const response = await getGuestToken(selectedDashboard.embed_guid); // Use embed_guid to fetch the token
          return response.data;
        } catch (error) {
          console.error('Guest token error', error);
        }
      };

      fetchGuestToken()
        .then((token) => {
          if (selectedDashboard && token && mountPoint) {
            embedDashboard({
              id: selectedDashboard.embed_guid,
              supersetDomain: supersetUrl, // Replace with your Superset URL
              mountPoint,
              fetchGuestToken: () => token,
              dashboardUiConfig: {
                hideTitle: true,
                filters: {
                  expanded: false,
                  visible: false,
                },
              },
            });
          }
        })
        .catch((error) => console.error('Error embedding dashboard:', error));
    } else {
      console.error('Chat token is missing or no dashboard selected');
    }
  }, [selectedDashboard]);

  const handleTabChange = (key) => {
    const dashboard = embeddDashboards.find((d) => {
      return d.embed_guid === key;
    });
    setSelectedDashboard(dashboard);
  };
  setTimeout(() => {
    const iframe = document.querySelector(`iframe`);
    if (iframe) {
      iframe.style.width = '100%'; // Set the width as needed
      iframe.style.minHeight = '100%'; // Set the height as needed
      // Add 2px85 black border
      iframe.style.borderRadius = '10px'; // Add rounded corners
      // iframe.style.backgroundColor = 'black'; // Optional background color
    }
  }, 3000);

  const sendMessage = async (question) => {
    const textToSend = question || input;
    if (textToSend.trim() === '') return;

    const newUserMessage = {
      sender: 'human',
      text: textToSend,
      fromHistory: false,
    };
    setMessages((prev) => [...prev, newUserMessage]);
    setIsThinking(true);
    setIsInputDisabled(true);
    setInput('');

    try {
      const requestData = {
        question: textToSend,
        session_id: sessionId,
        ds_name: dsName,
      };
      const response = await getChatResponse(requestData);
      const ParsedAnswer = JSON.parse(response.data.answer);

      const botResponse = {
        chat_id: response.data.chat_id,
        sender: 'ai',
        text: ParsedAnswer.text || 'No text generated.',
        query: ParsedAnswer.query,
        data: ParsedAnswer.data && ParsedAnswer.data.length >= 1 ? (typeof ParsedAnswer.data === 'string' ? JSON.parse(ParsedAnswer.data) : ParsedAnswer.data) : null,
        fromHistory: false,
      };

      setMessages((prev) => [...prev, botResponse]);
    } catch (error) {
      const errorMsg = error?.response?.data?.error || 'Unknown error';
      setMessages((prev) => [...prev, { sender: 'ai', text: `Error: ${errorMsg}` }]);
    } finally {
      setIsThinking(false);
      setIsInputDisabled(false);
    }
  };

  const handleSelect = (item, sampleQues = false) => {
    const text = sampleQues === true ? item : item.description || '';
    setInput(text);

    // Focus and move cursor to end of text
    setTimeout(() => {
      if (inputRef.current && text.length) {
        inputRef.current.focus();
        inputRef.current.setSelectionRange(text.length, text.length);
      }
    }, 0);
  };

  const handleDelete = async (item) => {
    const guid = localStorage.getItem('guid');
    try {
      await deleteFavoriteById(guid, item.id);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Successfully removed ${item.name} from favorites.`);
      setFavoritesReload((prev) => !prev);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', `Error: ${error?.response?.data?.error}`);
      console.error('Error deleting favorite', error);
    }
  };

  const MAX_NAME_LENGTH = 25;

  const shortenName = (name) => (name.length > MAX_NAME_LENGTH ? `${name.slice(0, MAX_NAME_LENGTH)}...` : name);

  const menu = (
    <Menu
      style={{
        maxHeight: '300px',
        overflowY: 'auto',
        padding: 0,
        borderRadius: '15px',
      }}>
      {favorites && favorites.length > 0 ? (
        favorites.map((item, index) => (
          <Menu.Item key={index} className='custom-menu-item' style={{ border: '1px solid #ddd', margin: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', minWidth: 250 }}>
              {item.name.length > MAX_NAME_LENGTH ? (
                <Tooltip title={item.name}>
                  <button
                    style={{
                      flexGrow: 1,
                      marginRight: '16px',
                      textAlign: 'left',
                    }}
                    onClick={() => handleSelect(item)}>
                    {index + 1}. {shortenName(item.name)}
                  </button>
                </Tooltip>
              ) : (
                <button
                  style={{
                    flexGrow: 1,
                    marginRight: '16px',
                    textAlign: 'left',
                  }}
                  onClick={() => handleSelect(item)}>
                  {index + 1}. {shortenName(item.name)}
                </button>
              )}
              <div>
                <Tooltip title='Update favorite'>
                  <button style={{ marginRight: '10px' }} onClick={() => handleEditFavorite(item)}>
                    <EditFilled style={{ color: '#4F90E7' }} />
                  </button>
                </Tooltip>
                <Tooltip title='Delete'>
                  <button style={{ color: '#FF5C5C' }} onClick={() => handleDelete(item)}>
                    <DeleteFilled />
                  </button>
                </Tooltip>
              </div>
            </div>
          </Menu.Item>
        ))
      ) : (
        <Menu.Item disabled>No favorites available</Menu.Item>
      )}
    </Menu>
  );

  const tooltipContent = (
    <div>
      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
        <tbody>
          <tr>
            <td className='info-popup-key'>Dataset:</td>
            <td className='info-popup-value'>{dsName || 'N/A'}</td>
          </tr>
          <tr>
            <td className='info-popup-key'>LLM Model:</td>
            <td className='info-popup-value'>{defaultModel || 'N/A'}</td>
          </tr>
          <tr>
            <td className='info-popup-key'>Version:</td>
            <td className='info-popup-value'>{appVersion || 'N/A'}</td>
          </tr>
        </tbody>
      </table>
    </div>
  );

  const handleEditValuesChange = (changedValues, allValues) => {
    setIsEditButtonDisabled(!allValues.name || !allValues.description);
  };

  // New functions for editing favorites
  const handleEditFavorite = (item) => {
    setSelectedFavorite(item);
    formEditFavorite.setFieldsValue({
      name: item.name,
      description: item.description,
    });
    setIsEditModalVisible(true);
  };

  const handleEditFavoriteSubmit = async (values) => {
    try {
      const guid = localStorage.getItem('guid') || '';
      const data = {
        name: values.name,
        description: values.description,
        type: 'Query',
      };
      await updateFavoriteById(guid, selectedFavorite.id, data);
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', `Updated ${values.name} favorite.`);
      setIsEditModalVisible(false);
      setFavoritesReload((prev) => !prev);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
      console.error('Error updating favorite', error);
    }
  };

  return (
    <ConfigProvider theme={whiteTheme}>
      {dashboardLength === 0 || !IsDashboard ? (
        // Only Fullscreen Chat Mode
        <Pane initialSize='25%' minSize='10%' maxSize='500px'>
          <Header
            style={{
              zIndex: 1,
              height: '40px',
              width: '100%',
              backgroundColor: '#ffffff',
              borderBottom: '1px solid #ccc',
              display: 'flex',
              justifyContent: 'space-between',
              padding: '0 20px',
            }}>
            <div style={{ display: 'flex', gap: 10 }}>
              <Dropdown overlay={menu} trigger={['click']}>
                <a href='#' onClick={(e) => e.preventDefault()} style={{ marginTop: '2px' }}>
                  <Text style={{ fontSize: 14, color: '#000' }}>⭐ My Favorites</Text>
                </a>
              </Dropdown>
            </div>
            <Tooltip
              title={tooltipContent}
              placement='bottomRight'
              color='white'
              overlayInnerStyle={{
                color: '#000',
                borderRadius: '18px',
                padding: '8px 12px',
              }}>
              <InfoCircleOutlined style={{ fontSize: 18, color: '#000', cursor: 'pointer' }} />
            </Tooltip>
          </Header>
          <Layout
            ref={chatContainerRef}
            style={{
              height: 'calc(90vh - 180px)',
              minHeight: '60vh',
              maxHeight: '80vh',
              overflow: 'auto',
              padding: '20px 50px',
              display: 'flex',
              flexDirection: 'column',
              background: '#ffffff',
            }}>
            {messages.length === 0 && sampleQuestions.length > 0 ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                  gap: '12px',
                  marginTop: 20,
                }}>
                {/* Heading -- full view */}
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <QuestionCircleOutlined style={{ fontSize: '20px', color: '#000', marginLeft: 10 }} />
                  <div
                    style={{
                      color: 'black',
                      fontSize: '20px',
                      marginLeft: 8,
                    }}>
                    Sample Questions
                  </div>
                </div>

                {/* Sample questions */}
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '12px',
                    padding: '10px',
                  }}>
                  {(() => {
                    const rows = [];
                    const chunkSize = 3;

                    for (let i = 0; i < sampleQuestions.length; i += chunkSize) {
                      rows.push(sampleQuestions.slice(i, i + chunkSize));
                    }

                    const MAX_WIDTH = 500;

                    return rows.map((row, rowIdx) => (
                      <div key={rowIdx} style={{ display: 'flex', gap: '12px' }}>
                        {row.map((question, idx) => (
                          <div
                            key={idx}
                            onClick={() => handleSelect(question, true)}
                            title={question}
                            style={{
                              background: '#EBEBEB',
                              border: '1px solid #e5e7eb',
                              borderRadius: '10px',
                              padding: '8px 16px',
                              cursor: 'pointer',
                              fontSize: '13px',
                              fontWeight: 500,
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              width: 'fit-content',
                              maxWidth: `${MAX_WIDTH}px`,
                              transition: 'all 0.2s ease-in-out',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.background = '#F0F0F0')}
                            onMouseLeave={(e) => (e.currentTarget.style.background = '#EBEBEB')}>
                            {question}
                          </div>
                        ))}
                      </div>
                    ));
                  })()}
                </div>
              </div>
            ) : (
              <>
                {messages.map((message, index) =>
                  message.sender === 'human' ? (
                    <UserMessage key={index} message={message} setFavoritesReload={setFavoritesReload} />
                  ) : (
                    <BotMessage key={index} message={message} setIsInputDisabled={setIsInputDisabled} useTypingEffect={!message.fromHistory} chatContainerRef={chatContainerRef} size={size} />
                  )
                )}

                {isThinking && (
                  <Row style={{ marginBottom: '10px' }} justify='start'>
                    <Col>
                      <Space>
                        <Avatar src='https://vidaimages.s3.ap-south-1.amazonaws.com/kouventa.svg' style={{ marginTop: '5px' }} />
                        <Text className='blinking-text' style={{ paddingLeft: '10px' }}>
                          Processing your request...
                        </Text>
                      </Space>
                    </Col>
                  </Row>
                )}
              </>
            )}

            {/* Update Favorite Modal (unchanged) */}
            <Modal
              title='Update Favorite'
              visible={isEditModalVisible}
              onCancel={() => {
                setIsEditModalVisible(false);
                formEditFavorite.resetFields();
              }}
              footer={null}
              bodyStyle={{ padding: '0px 10px', paddingBottom: '1px' }}
              width={450}>
              <Form form={formEditFavorite} onFinish={handleEditFavoriteSubmit} layout='vertical' onValuesChange={handleEditValuesChange}>
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
                <Form.Item
                  label={
                    <span>
                      Question <span style={{ color: 'red' }}>*</span>
                    </span>
                  }
                  name='description'
                  rules={[{ message: 'Please input the question.' }]}
                  style={{ marginBottom: 5 }}>
                  <Input.TextArea placeholder='Enter question' rows={4} style={{ resize: 'none' }} />
                </Form.Item>
                <Form.Item>
                  <Button type='primary' htmlType='submit' block disabled={isEditButtonDisabled} style={{ width: '20%', float: 'right', marginBottom: -20 }}>
                    Update
                  </Button>
                </Form.Item>
              </Form>
            </Modal>
          </Layout>

          <div
            style={{
              position: 'fixed',
              bottom: 0,
              left: size === '0%' ? 0 : size,
              right: 0,
              background: '#ffffff',
              zIndex: 100,
              boxShadow: '0 -1px 5px rgba(0,0,0,0.1)',
            }}>
            <Footer
              style={{
                minHeight: '70px',
                maxHeight: '70px',
                margin: 0,
                padding: '10px 50px',
                background: '#ffffff',
              }}>
              <Input
                ref={inputRef}
                placeholder='Ask your data query in natural language...'
                value={input}
                disabled={isInputDisabled}
                onChange={(e) => setInput(e.target.value)}
                onPressEnter={() => sendMessage()}
                suffix={<Button type='primary' icon={<SendOutlined />} onClick={() => sendMessage()} size='small' disabled={input.trim() === '' || isInputDisabled} style={{ marginRight: '5px' }} />}
                style={{
                  borderRadius: '10px',
                  padding: '5px',
                  width: '100%',
                  marginTop: '8px',
                }}
              />
            </Footer>
            <div
              style={{
                padding: '8px 20px',
                fontSize: '12px',
                borderTop: '1px solid #ccc',
                textAlign: 'center',
                background: '#f5f5f5',
              }}>
              ⚠️ Disclaimer: This is a Business Insights Chatbot designed to answer business questions using your data. It may occasionally provide incorrect answers; please verify with other sources
              when needed.
            </div>
          </div>
        </Pane>
      ) : (
        <SplitPane split='vertical' size={size} allowResize={false}>
          <Pane
            style={{
              background: '',
              height: 'calc(100vh - 50px)',
              flex: '0 0 auto',
              overflowY: 'auto',
            }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'end',
                borderBottom: '1px solid #ccc',
                paddingBottom: '9px',
              }}>
              <div className=' mr-5'>
                <Button
                  type='default'
                  size='small'
                  onClick={handleCollapseLeft}
                  style={{
                    border: '1px solid #ccc',
                    borderRadius: '6px',
                    padding: '2px 2px',
                    background: '#f9f9f9',
                    boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                    marginTop: '6px',
                  }}
                  icon={
                    isMaximized ? (
                      <VerticalAlignBottomOutlined
                        style={{
                          transform: 'rotate(90deg)',
                          color: '#1890ff',
                          fontSize: '16px',
                        }}
                      />
                    ) : (
                      <VerticalAlignBottomOutlined
                        style={{
                          transform: 'rotate(-90deg)',
                          color: '#1890ff',
                          fontSize: '16px',
                        }}
                      />
                    )
                  }
                />
              </div>
            </div>
            <div>
              <Tabs
                defaultActiveKey='1'
                tabPosition='top'
                onChange={handleTabChange}
                items={embeddDashboards.map((dashboard) => ({
                  label: dashboard.name,
                  key: dashboard.embed_guid,
                  // children: <div id="superset-container" style={{ height: "100%" }}></div>, // Container for embedding
                }))}
                style={{ paddingLeft: '18px', paddingRight: '18px' }}
              />
              <div id='superset-container' className='w-full h-[73vh]  p-3'></div>
            </div>
          </Pane>
          <Pane initialSize='25%' minSize='10%' maxSize='500px'>
            <Header
              style={{
                zIndex: 1,
                height: '40px',
                width: '100%',
                backgroundColor: '#ffffff',
                borderBottom: '1px solid #ccc',
                display: 'flex',
                justifyContent: 'space-between',
                padding: '0 20px',
              }}>
              <div style={{ display: 'flex', gap: 10 }}>
                <Button
                  type='default'
                  size='small'
                  onClick={handleCollapse}
                  style={{
                    border: '1px solid #ccc',
                    borderRadius: '6px',
                    padding: '2px 2px',
                    background: '#f9f9f9',
                    boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
                    marginTop: '6px',
                  }}
                  icon={
                    size !== '0%' ? (
                      <VerticalAlignBottomOutlined
                        style={{
                          transform: 'rotate(90deg)',
                          color: '#1890ff',
                          fontSize: '16px',
                        }} // smaller icon
                      />
                    ) : (
                      <VerticalAlignBottomOutlined
                        style={{
                          transform: 'rotate(-90deg)',
                          color: '#1890ff',
                          fontSize: '16px',
                        }}
                      />
                    )
                  }
                />

                <Dropdown overlay={menu} trigger={['click']}>
                  <a href='#' onClick={(e) => e.preventDefault()} style={{ marginTop: '2px' }}>
                    <Text style={{ fontSize: 14, color: '#000' }}>⭐ My Favorites</Text>
                  </a>
                </Dropdown>
              </div>

              <Tooltip
                title={tooltipContent}
                placement='bottomRight'
                color='white'
                overlayInnerStyle={{
                  color: '#000',
                  borderRadius: '18px',
                  padding: '8px 12px',
                }}>
                <InfoCircleOutlined style={{ fontSize: 18, color: '#000', cursor: 'pointer' }} />
              </Tooltip>
            </Header>
            <Layout
              ref={chatContainerRef}
              style={{
                height: 'calc(90vh - 180px)',
                minHeight: '60vh',
                maxHeight: '80vh',
                overflow: 'auto',
                padding: '20px 50px',
                display: 'flex',
                flexDirection: 'column',
                background: '#ffffff',
              }}>
              {messages.length === 0 && sampleQuestions.length > 0 ? (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    gap: '12px',
                    marginTop: 20,
                  }}>
                  {/* Heading */}
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    <QuestionCircleOutlined
                      style={{
                        fontSize: '20px',
                        color: '#000',
                        marginLeft: 10,
                      }}
                    />
                    <div
                      style={{
                        color: 'black',
                        fontSize: '20px',
                        marginLeft: 8,
                      }}>
                      Sample Questions
                    </div>
                  </div>

                  {/* Sample questions */}
                  <div
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                      padding: '10px',
                    }}>
                    {(() => {
                      const rows = [];
                      const chunkSize = 3; // max 3 chips per row

                      for (let i = 0; i < sampleQuestions.length; i += chunkSize) {
                        rows.push(sampleQuestions.slice(i, i + chunkSize));
                      }

                      return rows.map((row, rowIdx) => (
                        <div key={rowIdx} style={{ display: 'flex', gap: '12px' }}>
                          {row.map((question, idx) => {
                            const truncated = question.length > 25 ? question.slice(0, 25) + '...' : question;

                            return (
                              <Tooltip key={idx} title={question} placement='top'>
                                <div
                                  onClick={() => setInput(question)}
                                  style={{
                                    background: '#EBEBEB',
                                    border: '1px solid #e5e7eb',
                                    borderRadius: '10px',
                                    padding: '8px 16px',
                                    cursor: 'pointer',
                                    fontSize: '13px',
                                    fontWeight: 500,
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    transition: 'all 0.2s ease-in-out',
                                    width: 'auto',
                                    maxWidth: '200px', // adjust for 25 chars
                                  }}
                                  onMouseEnter={(e) => (e.currentTarget.style.background = '#F0F0F0')}
                                  onMouseLeave={(e) => (e.currentTarget.style.background = '#EBEBEB')}>
                                  {truncated}
                                </div>
                              </Tooltip>
                            );
                          })}
                        </div>
                      ));
                    })()}
                  </div>
                </div>
              ) : (
                <>
                  {messages.map((message, index) =>
                    message.sender === 'human' ? (
                      <UserMessage key={index} message={message} setFavoritesReload={setFavoritesReload} />
                    ) : (
                      <BotMessage key={index} message={message} setIsInputDisabled={setIsInputDisabled} useTypingEffect={!message.fromHistory} chatContainerRef={chatContainerRef} size={size} />
                    )
                  )}

                  {isThinking && (
                    <Row style={{ marginBottom: '10px' }} justify='start'>
                      <Col>
                        <Space>
                          <Avatar src='https://vidaimages.s3.ap-south-1.amazonaws.com/kouventa.svg' style={{ marginTop: '5px' }} />
                          <Text className='blinking-text' style={{ paddingLeft: '10px' }}>
                            Processing your request...
                          </Text>
                        </Space>
                      </Col>
                    </Row>
                  )}
                </>
              )}

              {/* Update Favorite Modal */}
              <Modal
                title='Update Favorite'
                visible={isEditModalVisible}
                onCancel={() => {
                  setIsEditModalVisible(false);
                  formEditFavorite.resetFields();
                }}
                footer={null}
                bodyStyle={{ padding: '0px 10px', paddingBottom: '1px' }}
                width={450}>
                <Form form={formEditFavorite} onFinish={handleEditFavoriteSubmit} layout='vertical' onValuesChange={handleEditValuesChange}>
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
                  <Form.Item
                    label={
                      <span>
                        Question <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='description'
                    rules={[{ message: 'Please input the question.' }]}
                    style={{ marginBottom: 5 }}>
                    <Input.TextArea placeholder='Enter question' rows={4} style={{ resize: 'none' }} />
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type='primary'
                      htmlType='submit'
                      block
                      disabled={isEditButtonDisabled}
                      style={{
                        width: '20%',
                        float: 'right',
                        marginBottom: -20,
                      }}>
                      Update
                    </Button>
                  </Form.Item>
                </Form>
              </Modal>
            </Layout>
            <div
              style={{
                position: 'fixed',
                bottom: 0,
                left: size === '0%' ? 0 : size, // Adjust if sidebar is collapsible
                right: 0,
                background: '#ffffff',
                zIndex: 100,
                boxShadow: '0 -1px 5px rgba(0,0,0,0.1)',
              }}>
              <Footer
                style={{
                  minHeight: '70px',
                  maxHeight: '70px',
                  margin: 0,
                  padding: '10px 50px',
                  background: '#ffffff',
                }}>
                <Input
                  ref={inputRef}
                  placeholder='Ask your data query in natural language...'
                  value={input}
                  disabled={isInputDisabled}
                  onChange={(e) => setInput(e.target.value)}
                  onPressEnter={() => {
                    sendMessage();
                    setInput('');
                  }}
                  suffix={
                    <Button
                      type='primary'
                      icon={<SendOutlined />}
                      onClick={() => {
                        sendMessage();
                        setInput('');
                      }}
                      size='small'
                      disabled={input.trim() === '' || isInputDisabled}
                      style={{ marginRight: '5px' }}
                    />
                  }
                  style={{
                    borderRadius: '10px',
                    padding: '5px',
                    width: '100%',
                    marginTop: '8px',
                  }}
                />
              </Footer>

              <div
                style={{
                  padding: '8px 20px',
                  fontSize: '12px',
                  borderTop: '1px solid #ccc',
                  textAlign: 'center',
                  background: '#f5f5f5',
                }}>
                ⚠️ Disclaimer: This is a Business Insights Chatbot designed to answer business questions using your data. It may occasionally provide incorrect answers; please verify with other
                sources when needed.
              </div>
            </div>
          </Pane>
        </SplitPane>
      )}
    </ConfigProvider>
  );
};

export default ChatInterface;
