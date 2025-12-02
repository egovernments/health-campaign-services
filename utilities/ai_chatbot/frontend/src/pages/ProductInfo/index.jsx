// Import pre-built libraries
import dayjs from 'dayjs';
import { v4 as uuidv4 } from 'uuid';
import { Parser } from 'node-sql-parser';
import { useState, useEffect, useMemo, useRef } from 'react';
import { Card, Button, Table, Layout, notification, Typography, Form, Row, Col, Select, Switch, Input, Space, Dropdown, Tooltip, Tag, Modal, Tabs, Descriptions, Menu, Empty, Radio } from 'antd';
import { DeleteFilled, MoreOutlined, CloseCircleOutlined, DatabaseOutlined, CheckCircleOutlined, InfoCircleOutlined, EditFilled, DeleteOutlined, EditOutlined, CopyOutlined } from '@ant-design/icons';

// Import custom styling
import './style.scss';

// Import Local Components
import LoadingWidget from '../../components/LoadingWidget';
import ResizableReact from '../../components/ResizeableReact';
import launchChatIcon from '../../assets/imgs/chat-icon.png';
import PageBreadcrumb from '../../components/BreadCrumb/BreadCrumb';

// Import services & helpers
import { validateFiveChar } from '../../utils/validation/validateFiveChar';
import {
  getAllConfigurations,
  deleteConfiguration,
  getConfigurationById,
  postPerformAction,
  updateConfigurationStatus,
  getAllTags,
  getDefaultPrompt,
  updateConfigurationById,
  getDefaultModel,
  getAllConnections,
  getMacros,
} from '../../services/ProductApis';

import ExamplesJsonImportExport from '../../components/ProductInfo/ExamplesJsonImportExport';

const ProductInfo = () => {
  // For Notifications
  const notificationProps = {
    className: 'custom-class',
    style: { width: 350 },
  };
  const showNotification = (icon, message, description) => {
    notification.open({ ...notificationProps, icon, message, description });
  };

  // Base UseStates
  const [tableData, setTableData] = useState([]);
  const [pageSize, setpageSize] = useState(10);
  const [tags, setTags] = useState([]);
  const [loading, setLoading] = useState(false);
  const [btndisabled, setbtndisabled] = useState(true);
  const [activeTabKey, setActiveTabKey] = useState('1');
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);

  // For Update Functionality
  const [isActive, setIsActive] = useState(false);
  const [selectedTags, setSelectedTags] = useState([]);
  const [currentRecord, setCurrentRecord] = useState(null);
  const [defaultPrompt, setDefaultPrompt] = useState(null);
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [isPromptModalVisible, setIsPromptModalVisible] = useState(false);
  const [isEditButtonDisabled, setEditIsButtonDisabled] = useState(false);
  const [updateModalLoading, setUpdateModalLoading] = useState(false);

  // For Form
  const [schemaDisabled, setSchemaDisabled] = useState(false);

  // For Connections
  const [connections, setConnections] = useState([]);
  const [selectedConnection, setSelectedConnection] = useState(null);

  // For SQL Examples
  const [sqlExampleModalData, setsqlExampleModalData] = useState({
    question: '',
    query: '',
    exampleType: 'Semantic',
  });
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [sqlExampleModalMode, setSqlExampleModalMode] = useState('add'); // 'add' | 'edit' | 'view'
  const [editingIndex, setEditingIndex] = useState(null);
  const [sqlError, setSqlError] = useState(null);

  // For Delete Modal
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteId, setDeleteId] = useState(null);
  const [deleteName, setDeleteName] = useState('');

  // For Sample Questions
  const [sampleQuestionModalData, setSampleQuestionModalData] = useState({
    sample_question: '',
  });
  const [isSampleQuestionModalVisible, setIsSampleQuestionModalVisible] = useState(false);
  const [sampleQuestionModalMode, setSampleQuestionModalMode] = useState('add'); // 'add' | 'edit' | 'view'
  const [editingIndexSampleQuestion, setEditingIndexSampleQuestion] = useState(null);

  // For macros
  const [isMacroModalVisible, setIsMacroModalVisible] = useState(false);
  const [macros, setMacros] = useState([]);

  // For Copy Functionality
  const [copied, setCopied] = useState(false);

  // Destructure Imported Components
  const [form] = Form.useForm();
  const { Header } = Layout;
  const { Text } = Typography;
  const { TextArea } = Input;
  const { TabPane } = Tabs;
  const { Option } = Select;
  const inputRef = useRef(null);

  // For Resizing Columns
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
      width: 15,
    },
    {
      title: <div style={{ textAlign: 'center' }}>Name</div>,
      dataIndex: 'name',
      key: 'name',
      width: 40,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      ellipsis: true,
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
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
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
    {
      key: 'actions',
      align: 'center',
      width: 30,
      title: <div style={{ justifyContent: 'center' }}>Actions</div>,
      onHeaderCell: (column) => ({
        width: column.width,
        onResize: handleResize(column),
      }),
      render: (record) => {
        return <ActionList record={record} style={{ justifyContent: 'center' }} />;
      },
      fixed: 'right',
    },
  ]);

  useEffect(() => {
    if (isCreateModalVisible && selectedConnection) {
      handleGetPrompt();
    }
  }, [selectedConnection, isCreateModalVisible]);

  // handling dbType change
  useEffect(() => {
    if (selectedConnection) {
      const currentSchema = form.getFieldValue('table_schema');

      if (selectedConnection.type === 'MySQL') {
        setSchemaDisabled(true);
        if (!currentSchema) {
          form.setFieldsValue({ table_schema: '' });
        }
      } else if (selectedConnection.type === 'PostgreSQL') {
        setSchemaDisabled(false);
        if (!currentSchema) {
          form.setFieldsValue({ table_schema: 'public' });
        }
      } else if (selectedConnection.type === 'MSSQL') {
        setSchemaDisabled(false);
        if (!currentSchema) {
          form.setFieldsValue({ table_schema: 'dbo' });
        }
      } else {
        setSchemaDisabled(false);
      }
    }
  }, [selectedConnection, form]);

  // Mount data on page reload
  useEffect(() => {
    fetchTableData();
  }, []);

  // Reset tab when modal opens
  useEffect(() => {
    if (isCreateModalVisible || isEditModalVisible) {
      setActiveTabKey('1');
    }
  }, [isCreateModalVisible, isEditModalVisible]);

  //  re-validate the required fields whenever selectedConnection changes
  useEffect(() => {
    const name = form.getFieldValue('name');
    const table_name = form.getFieldValue('table_name');
    const table_schema = form.getFieldValue('table_schema');
    const isMysql = selectedConnection?.type === 'MySQL';

    const allRequiredFieldsFilled = name && table_name && (isMysql || table_schema) && selectedConnection;

    if (isCreateModalVisible) {
      setbtndisabled(!allRequiredFieldsFilled);
    } else if (isEditModalVisible) {
      setEditIsButtonDisabled(!allRequiredFieldsFilled);
    }
  }, [selectedConnection, form, isCreateModalVisible, isEditModalVisible]);

  const NOT_ALLOWED_QUERIES = ['insert', 'update', 'delete', 'drop', 'alter', 'create', 'truncate', 'merge', 'rename'];

  const validateSQL = (sql) => {
    // Check for disallowed keywords (case-insensitive, whole words only)
    for (const keyword of NOT_ALLOWED_QUERIES) {
      const regex = new RegExp(`\\b${keyword}\\b`, 'i');
      if (regex.test(sql)) {
        setSqlError(`Use of "${keyword.toUpperCase()}" is not allowed.`);
        return false;
      }
    }

    try {
      const parser = new Parser();
      let dialect = 'postgresql';

      const type = JSON.parse(selectedConnection?.data || '{}')?.db_type;
      if (type === 'PostgreSQL') dialect = 'postgresql';
      else if (type === 'MSSQL') dialect = 'transactsql';
      else if (type === 'MySQL') dialect = 'mysql';

      parser.astify(sql, { database: dialect });
      setSqlError(null);
      return true;
    } catch (e) {
      setSqlError('Invalid SQL query.');
      return false;
    }
  };

  // this handles the resizing of columns
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

  // Colors for Tags
  const colors = ['purple', 'blue', 'volcano', 'orange', 'gold', 'lime', 'green', 'cyan', 'red', 'geekblue', 'magenta'];
  const getColorByIndex = (index) => colors[index % colors.length];

  // handles for Modals
  const showPromptModal = () => {
    setIsPromptModalVisible(true);
  };

  const handlePromptModalCancel = () => {
    setIsPromptModalVisible(false);
  };

  const openDeleteModal = (id, name) => {
    setDeleteId(id);
    setDeleteName(name);
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = async () => {
    setIsDeleteModalOpen(false);
    await deleteHandler(deleteId);
  };

  // Update the handleGetPrompt function
  const handleGetPrompt = async (record = null) => {
    try {
      let dbType = 'PostgreSQL'; // Default to PostgreSQL

      // Determine the database type
      if (record?.data) {
        dbType = JSON.parse(record.data).db_type;
      } else if (selectedConnection?.type) {
        dbType = selectedConnection.type;
      }

      // If no specific type is found, keep PostgreSQL as default
      const prompt = await getDefaultPrompt(dbType);
      if (prompt?.data) setDefaultPrompt(prompt.data);
    } catch (err) {
      console.error('Error fetching prompt:', err);
      // Fallback to PostgreSQL if there's an error
      try {
        const fallbackPrompt = await getDefaultPrompt('PostgreSQL');
        if (fallbackPrompt?.data) setDefaultPrompt(fallbackPrompt.data);
      } catch (fallbackErr) {
        console.error('Error fetching fallback prompt:', fallbackErr);
      }
    }
  };

  const handleCreateCancel = () => {
    setTimeout(() => {
      form.resetFields();
      setSchemaDisabled(false);
      setIsCreateModalVisible(false);
    }, 100);
  };

  const showCreateModal = () => {
    setCurrentRecord(null);
    setSelectedConnection(null);
    form.resetFields();
    setSchemaDisabled(false);
    setIsCreateModalVisible(true);
    handleGetPrompt();
  };

  const handleEditCancel = () => {
    // Reset form after a small delay to ensure all operations complete
    setTimeout(() => {
      form.resetFields();
      setSchemaDisabled(false);
      setCurrentRecord(null);
      setIsEditModalVisible(false);
      setUpdateModalLoading(false);
    }, 100);
  };

  // Helper function to safely get form array values
  const getSafeFormArrayValue = (form, fieldName) => {
    try {
      const value = form.getFieldValue(fieldName);
      return Array.isArray(value) ? value : [];
    } catch (error) {
      console.warn(`Error getting form field ${fieldName}:`, error);
      return [];
    }
  };

  const setSafeFormArrayValue = (form, fieldName, value) => {
    try {
      form.setFieldsValue({ [fieldName]: Array.isArray(value) ? value : [] });
    } catch (error) {
      console.warn(`Error setting form field ${fieldName}:`, error);
    }
  };

  const showEditModal = async (record) => {
    setIsEditModalVisible(true);
    setCurrentRecord(null);
    setSchemaDisabled(false);
    setUpdateModalLoading(true);

    try {
      const response = await getConfigurationById(record.id);
      const data = JSON.parse(response.data.data);
      await fetchConnections();

      const tagNames = response.data.tags.map((tag) => tag.name);
      const tagIds = response.data.tags.map((tag) => tag.id);
      const connResponse = await getAllConnections();
      const currentConn = connResponse.data.find((c) => c.id === data.conn_id);

      setSelectedConnection(currentConn);
      setSelectedTags(tagIds);
      setIsActive(record.is_active);

      // FIX: Ensure examples and questions have proper structure
      const examples = Array.isArray(response.data.examples)
        ? response.data.examples.map((example) => ({
            id: example.id || -1,
            key: example.key || '',
            value: example.value || '',
            type: example.type || 'sql_example',
          }))
        : [];

      const sampleQuestions = Array.isArray(response.data.questions)
        ? response.data.questions.map((q) => ({
            id: q.id || -1,
            key: q.detail || '', // Use detail as key for display
            detail: q.detail || '', // Keep original detail field
          }))
        : [];

      const mappedRecord = {
        ...data,
        table_name: data.db_type === 'Elasticsearch' ? data.index_name : data.table_name,
        tag_name: tagNames,
        tag_ids: tagIds,
        id: record.id,
        examples: examples,
      };

      setCurrentRecord(mappedRecord);

      // FIX: Use setTimeout to ensure form is ready before setting values
      setTimeout(() => {
        form.setFieldsValue({
          name: mappedRecord.name,
          db_type: mappedRecord.db_type,
          table_name: mappedRecord.table_name,
          table_schema: mappedRecord.table_schema,
          connections: mappedRecord.id,
          custom_prompt: mappedRecord.custom_prompt ? base64ToUtf8(mappedRecord.custom_prompt) : '',
          business_rules: mappedRecord.business_rules ? base64ToUtf8(mappedRecord.business_rules) : '',
          tags: tagIds,
          examples: examples,
          sample_question: sampleQuestions,
        });
        setUpdateModalLoading(false);
      }, 0);

      // Fetch prompt based on the record's database type
      handleGetPrompt(record);
    } catch (error) {
      const error_msg = error?.response?.data?.error || 'Unable to fetch dataset configuration.';
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error_msg);
      setUpdateModalLoading(false);
    }
  };
  // Add this utility function at the top of your component
  // const utf8ToBase64 = (str) => {
  //   try {
  //     return btoa(unescape(encodeURIComponent(str)));
  //   } catch (error) {
  //     console.error('Base64 encoding error:', error);
  //     return '';
  //   }
  // };

  const utf8ToBase64 = (str) => {
    try {
      const encoder = new TextEncoder();
      const bytes = encoder.encode(str);
      let binary = '';
      const chunkSize = 0x8000;

      for (let i = 0; i < bytes.length; i += chunkSize) {
        const chunk = bytes.subarray(i, i + chunkSize);
        binary += String.fromCharCode.apply(null, chunk);
      }

      return btoa(binary);
    } catch (err) {
      console.error('Base64 encoding failed:', err);
      return '';
    }
  };

  // const base64ToUtf8 = (str) => {
  //   try {
  //     return decodeURIComponent(escape(atob(str)));
  //   } catch (error) {
  //     console.error('Base64 decoding error:', error);
  //     return '';
  //   }
  // };

  const base64ToUtf8 = (base64Str) => {
    try {
      const binary = atob(base64Str);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      const decoder = new TextDecoder();
      return decoder.decode(bytes);
    } catch (err) {
      console.error('Base64 decoding failed:', err);
      return '';
    }
  };

  const showSqlAddModal = (index = null) => {
    const values = form.getFieldValue('examples') || [];
    setSqlError(null);
    if (index !== null) {
      const selected = values[index];
      const validType = selected.type === 'Core' || selected.type === 'Semantic' ? selected.type : 'Semantic';
      setsqlExampleModalData({
        question: selected.key,
        query: selected.value,
        exampleType: validType || 'Semantic',
      });
      setEditingIndex(index);
      setSqlExampleModalMode('edit');
    } else {
      setsqlExampleModalData({ question: '', query: '', exampleType: 'Semantic' });
      setEditingIndex(null);
      setSqlExampleModalMode('add');
    }
    setIsModalVisible(true);
  };

  const showSqlViewModal = (index) => {
    const values = form.getFieldValue('examples') || [];
    const selected = values[index];
    const validType = selected.type === 'Core' || selected.type === 'Semantic' ? selected.type : 'Semantic';
    setsqlExampleModalData({
      question: selected.key,
      query: selected.value,
      exampleType: validType || 'Semantic',
    });
    setEditingIndex(index);
    setSqlExampleModalMode('view');
    setSqlError(null);
    setIsModalVisible(true);
  };

  const showSampleQuestionAddModal = (index = null) => {
    const values = form.getFieldValue('sample_question') || [];
    if (index !== null) {
      const selected = values[index];
      setSampleQuestionModalData({ sample_question: selected.key });
      setEditingIndexSampleQuestion(index);
      setSampleQuestionModalMode('edit');
    } else {
      setSampleQuestionModalData({ sample_question: '' });
      setEditingIndexSampleQuestion(null);
      setSampleQuestionModalMode('add');
    }
    setIsSampleQuestionModalVisible(true);
  };

  const showSampleQuestionViewModal = (index) => {
    const values = form.getFieldValue('sample_question') || [];
    const selected = values[index];
    setSampleQuestionModalData({ sample_question: selected.key });
    setEditingIndexSampleQuestion(index);
    setSampleQuestionModalMode('view');
    setIsSampleQuestionModalVisible(true);
  };

  const handleSampleQuestionDelete = (index) => {
    const updated = [...getSafeFormArrayValue(form, 'sample_question')];
    updated.splice(index, 1);
    setSafeFormArrayValue(form, 'sample_question', updated);
    onValuesChangeEdit();
  };

  const handleSampleQuestionAdd = () => {
    if (!sampleQuestionModalData.sample_question.trim()) {
      Modal.error({
        title: 'Validation Error',
        content: 'Sample question is required.',
      });
      return;
    }

    const values = form.getFieldValue('sample_question') || [];
    const updated = [...values];

    const oldId = editingIndexSampleQuestion !== null && values[editingIndexSampleQuestion]?.id;

    const newSampleQuestionEntry = {
      id: oldId !== undefined ? oldId : -1, // keep existing id, -1 for new
      key: sampleQuestionModalData.sample_question.trim(),
      detail: sampleQuestionModalData.sample_question.trim(),
    };

    if (editingIndexSampleQuestion !== null) {
      updated[editingIndexSampleQuestion] = newSampleQuestionEntry;
    } else {
      updated.unshift(newSampleQuestionEntry);
    }

    form.setFieldsValue({ sample_question: updated }); // ✅ correct field
    onValuesChangeEdit();
    setIsSampleQuestionModalVisible(false);
  };

  // In the Create modal's onValuesChange function:
  const onValuesChange = () => {
    const name = form.getFieldValue('name');
    const table_name = form.getFieldValue('table_name');
    const table_schema = form.getFieldValue('table_schema');

    const dbType = selectedConnection?.type;
    const isMysql = dbType === 'MySQL';
    const isElastic = dbType === 'Elasticsearch';

    // For MySQL + Elasticsearch, schema is not required
    const allRequiredFieldsFilled = name && table_name && (isMysql || isElastic || table_schema) && selectedConnection;

    setbtndisabled(!allRequiredFieldsFilled);
  };

  // In the Update modal's onValuesChange function:
  const onValuesChangeEdit = () => {
    const name = form.getFieldValue('name');
    const table_name = form.getFieldValue('table_name');
    const table_schema = form.getFieldValue('table_schema');

    const dbType = selectedConnection?.type;
    const isMysql = dbType === 'MySQL';
    const isElastic = dbType === 'Elasticsearch';

    // For MySQL + Elasticsearch, schema is not required
    const allRequiredFieldsFilled = name && table_name && (isMysql || isElastic || table_schema) && selectedConnection;

    setEditIsButtonDisabled(!allRequiredFieldsFilled);
  };

  // To copy the default prompt to clipboard
  const handleCopy = () => {
    const formattedElement = document.getElementById('formattedPromptHTML');
    if (!formattedElement) {
      console.error('Element with ID "formattedPromptHTML" not found.');
      return;
    }

    const textToCopy = formattedElement.textContent || formattedElement.innerText;

    // Create a temporary textarea element
    const textarea = document.createElement('textarea');
    textarea.value = textToCopy;

    // Avoid scrolling to bottom of the page
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';

    // Add the textarea to the DOM
    document.body.appendChild(textarea);

    // Select the text inside the textarea
    textarea.focus();
    textarea.select();

    try {
      // Execute the copy command
      const successful = document.execCommand('copy');
      if (successful) {
        setCopied(true);
        // Show "Copied!" feedback for a bit longer
        setTimeout(() => setCopied(false), 2000);
      } else {
        console.error('Copy command was unsuccessful.');
      }
    } catch (err) {
      console.error('Copy failed:', err);
    }

    // Remove the temporary textarea from the DOM
    document.body.removeChild(textarea);
  };

  // To format the default prompt in Modal
  const formattedPrompt = useMemo(() => {
    if (!defaultPrompt) return '';

    return defaultPrompt
      .replace(/\{(.*?)\}/g, '<span style="color: #1890ff; font-weight: 500;">{$1}</span>') // Highlight placeholders
      .replace(/\n/g, '<br/>'); // Preserve line breaks
  }, [defaultPrompt]);

  // Fetching Data
  const fetchTableData = async () => {
    setLoading(true);
    try {
      const res = await getAllConfigurations();
      let parsedData = [];
      if (res.data && res.data.length > 0) {
        // Check if res.data exists & is not empty
        parsedData = res.data.map((item) => {
          const parsedItem = JSON.parse(item.data);
          const parsedTag = item.tags;
          const tagNames = parsedTag.map((tag) => tag.name);
          const tagIds = parsedTag.map((tag) => tag.id);
          return {
            ...item,
            db_type: parsedItem.db_type,
            table_name: parsedItem.db_type === 'Elasticsearch' ? parsedItem.index_name : parsedItem.table_name,
            tag_name: tagNames,
            tag_ids: tagIds,
          };
        });
      }
      setTableData(parsedData);
      setLoading(false);
    } catch (error) {
      setLoading(false);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch data.');
    }
  };

  const fetchTags = async () => {
    try {
      const data = await getAllTags();
      const tagsArray = Array.isArray(data.data) ? data.data : [];
      setTags(tagsArray);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch tags.');
      setTags([]);
    }
  };

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

  const refreshTableData = async () => {
    try {
      const res = await getAllConfigurations();
      let parsedData = [];
      if (res.data && res.data.length > 0) {
        // Check if res.data exists & is not empty
        parsedData = res.data.map((item) => {
          const parsedItem = JSON.parse(item.data);
          const parsedTag = item.tags;

          const tagNames = parsedTag.map((tag) => tag.name);
          const tagIds = parsedTag.map((tag) => tag.id);
          return {
            ...item,
            db_type: parsedItem.db_type,
            table_name: parsedItem.db_type === 'Elasticsearch' ? parsedItem.index_name : parsedItem.table_name,
            tag_name: tagNames,
            tag_ids: tagIds,
          };
        });
      }
      // Set tableData and filteredData to the parsed data or an empty array if no data is returned
      setTableData(parsedData);
    } catch (error) {
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch data.');
    }
  };

  const fetchMacros = async () => {
    try {
      const data = await getMacros();
      setMacros(data.data);
      setIsMacroModalVisible(true);
    } catch (error) {
      console.error('Error fetching macros:', error);
    }
  };

  // CRUD Operations
  const handleCreate = () => {
    setLoading(true);
    form
      .validateFields()
      .then((values) => {
        const tag_ids = form.getFieldValue('tags')?.join(',') || '';
        const formData = {
          name: form.getFieldValue('name'),
          description: '',
          conn_id: selectedConnection.id,
          db_type: selectedConnection.type,
          table_schema: form.getFieldValue('table_schema') || null,
          ...(selectedConnection.type === 'Elasticsearch' ? { index_name: form.getFieldValue('table_name') } : { table_name: form.getFieldValue('table_name') }),
          custom_prompt: form.getFieldValue('custom_prompt') !== undefined ? utf8ToBase64(form.getFieldValue('custom_prompt')) : '',
          examples: values.examples
            ? utf8ToBase64(
                JSON.stringify(
                  values.examples.map((example, index) => ({
                    ...example,
                    id: -1,
                  }))
                )
              )
            : '',
          questions: values.sample_question
            ? utf8ToBase64(
                JSON.stringify(
                  values.sample_question.map((q) => ({
                    id: q.id,
                    detail: q.key,
                  }))
                )
              )
            : '',
          business_rules: form.getFieldValue('business_rules') !== undefined ? utf8ToBase64(form.getFieldValue('business_rules')) : '',
          model_id: '-1',
          is_enabled: true,
          tag_ids,
        };
        postPerformAction(formData)
          .then((response) => {
            setLoading(false);
            form.resetFields();
            notification.success({
              message: 'Success',
              description: 'Configuration created successfully.',
              style: { width: 350 },
            });
            setIsCreateModalVisible(false);
            setSelectedConnection(null);
            setbtndisabled(true);
            fetchTableData();
          })
          .catch((error) => {
            notification.error({
              message: 'Error',
              description: error?.response?.data?.error,
            });
            setLoading(false);
          });
      })
      .catch((errorInfo) => {
        setLoading(false);
        console.error('Validation Failed:', errorInfo);
      });
  };

  const handleUpdate = () => {
    setLoading(true);
    form
      .validateFields()
      .then((values) => {
        // Safely get form values with fallbacks
        const examples = form.getFieldValue('examples') || [];
        const sampleQuestions = form.getFieldValue('sample_question') || [];

        const formData = {
          name: form.getFieldValue('name'),
          conn_id: selectedConnection.id,
          db_type: selectedConnection.type,
          description: '',
          table_schema: form.getFieldValue('table_schema') || null,
          ...(selectedConnection.type === 'Elasticsearch' ? { index_name: values.table_name } : { table_name: values.table_name }),
          custom_prompt: form.getFieldValue('custom_prompt') !== undefined ? utf8ToBase64(form.getFieldValue('custom_prompt')) : '',
          // FIX: Ensure examples are always properly encoded, even if empty
          examples:
            examples.length > 0
              ? utf8ToBase64(
                  JSON.stringify(
                    examples.map((example) => ({
                      ...example,
                      id: example.id || -1, // Preserve existing IDs
                    }))
                  )
                )
              : utf8ToBase64(JSON.stringify([])), // Explicit empty array
          // FIX: Ensure sample questions are always properly encoded
          questions:
            sampleQuestions.length > 0
              ? utf8ToBase64(
                  JSON.stringify(
                    sampleQuestions.map((q) => ({
                      id: q.id !== undefined && q.id !== null ? q.id : -1,
                      detail: q.detail || q.key, // Handle both field names
                    }))
                  )
                )
              : utf8ToBase64(JSON.stringify([])), // Explicit empty array
          business_rules: form.getFieldValue('business_rules') !== undefined ? utf8ToBase64(form.getFieldValue('business_rules')) : '',
          model_id: '-1',
          is_enabled: isActive,
          tag_ids: selectedTags.join(',') || '',
        };

        updateConfigurationById(formData, currentRecord.id)
          .then(() => {
            setLoading(false);
            setIsEditModalVisible(false);
            // Comment refreshTableData handler, as after updating the modal the OS was crashing due to memory spike between the two api calls.
            // refreshTableData();
            notification.success({
              message: 'Success',
              description: 'Configuration updated successfully.',
              style: { width: 350 },
            });
          })
          .catch((error) => {
            notification.error({
              message: 'Error',
              description: error?.response ? error?.response?.data?.error : error?.message,
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

  const deleteHandler = async (id) => {
    setLoading(true);
    try {
      await deleteConfiguration(id);
      setLoading(false);
      await fetchTableData();
      showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Success', 'Configuration with id ' + id + ' deleted successfully.');
    } catch (error) {
      setLoading(false);
      console.error('Error deleting resource:', error?.response?.data?.error);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error);
    }
  };

  // For Connections
  const fetchConnections = async () => {
    try {
      const data = await getAllConnections();
      setConnections(data.data);
    } catch (error) {
      console.error('Error fetching connections:', error?.response?.data?.error);
      showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Error', error?.response?.data?.error || 'Failed to fetch connections.');
    }
  };

  // For SQL Examples - Create & Update
  const handleSqlAdd = () => {
    if (!sqlExampleModalData.question.trim() || !sqlExampleModalData.query.trim()) {
      Modal.error({
        title: 'Validation Error',
        content: 'Both question and SQL query are required.',
      });
      return;
    }
    const values = form.getFieldValue('examples') || [];
    const updated = [...values];

    // Preserve id if editing; otherwise use -1 for new entries
    const oldId = editingIndex !== null && values[editingIndex]?.id;

    const newSqlExampleEntry = {
      id: oldId !== undefined ? oldId : -1,
      key: sqlExampleModalData.question.trim(),
      value: sqlExampleModalData.query.trim(),
      type: sqlExampleModalData.exampleType,
    };

    if (editingIndex !== null) {
      updated[editingIndex] = newSqlExampleEntry;
    } else {
      updated.unshift(newSqlExampleEntry);
    }

    form.setFieldsValue({ examples: updated });
    onValuesChangeEdit();
    setIsModalVisible(false);
  };

  // For SQL Examples - DELETE
  const handleSqlDelete = (index) => {
    const updated = [...getSafeFormArrayValue(form, 'examples')];
    updated.splice(index, 1);
    setSafeFormArrayValue(form, 'examples', updated);
    onValuesChangeEdit();
  };

  // This is the actionlist
  const ActionList = ({ record }) => {
    const [isActive, setIsActive] = useState(record.is_active);

    const updateStatusHandler = async (newStatus) => {
      try {
        const response = await updateConfigurationStatus({ status: newStatus }, record.id);
        if (response.status !== 200) {
          throw new Error('Failed to update status');
        }
        setTimeout(() => {
          setIsActive(newStatus);
        }, 1000);
        await refreshTableData();
      } catch (error) {
        console.error('Error updating status:', error?.response?.data?.error);
      }
    };

    const items = [
      {
        key: 'activate',
        label: (
          <div
            style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
            onClick={() => {
              const newStatus = !isActive;
              updateStatusHandler(newStatus);
            }}>
            <Switch
              size='small'
              style={{ transform: 'scale(0.6)', marginLeft: '-5px' }}
              onChange={(isActive) => {
                const newStatus = isActive;
                updateStatusHandler(newStatus);
              }}
              checked={isActive}
            />
            <span style={{ marginLeft: '8px' }}>{isActive ? 'Inactive' : 'Active'}</span>
          </div>
        ),
      },
      {
        key: 'update',
        label: (
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <EditFilled style={{ marginRight: '8px' }} />
            Update
          </div>
        ),
        onClick: () => {
          showEditModal(record);
          fetchTags();
          handleGetPrompt(record);
        },
      },
      {
        key: 'delete',
        label: (
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <DeleteFilled style={{ color: '#ff0000', marginRight: '8px' }} />
            Delete
          </div>
        ),
        onClick: () => openDeleteModal(record.id, record.name),
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

  // Menu of Connection
  const connectionMenu = (
    <Menu
      onClick={({ key }) => {
        const selectedConn = connections.find((conn) => conn.id.toString() === key);
        if (selectedConn) {
          setSelectedConnection(selectedConn);
        }
      }}>
      {connections.length > 0 ? (
        connections.map((conn) => (
          <Menu.Item key={conn.id.toString()}>
            <span>{conn.name}</span>
          </Menu.Item>
        ))
      ) : (
        <Menu.Item key='no-connections' disabled>
          No connections
        </Menu.Item>
      )}
    </Menu>
  );

  return (
    <>
      <PageBreadcrumb title='Bot Configurations' />

      {/* Header */}
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
            <span
              style={{
                fontSize: '20px',
                color: 'black',
                marginLeft: '10px',
                fontWeight: 'bold',
              }}>
              Bot Configurations
            </span>
          </div>
          <div>
            <Button
              type='primary'
              onClick={() => {
                showCreateModal();
                fetchTags();
                fetchConnections();
                handleGetPrompt();
              }}
              style={{ marginRight: '8px' }}>
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
              dataSource={tableData}
              components={{ header: { cell: ResizableReact } }}
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

      {/* CREATE MODAL */}
      <Modal title='Create Bot Configuration' visible={isCreateModalVisible} onCancel={handleCreateCancel} footer={null} style={{ top: '5%', transition: 'none' }} afterClose={() => form.resetFields()} width={1000}>
        <Form form={form} layout='vertical' size='middle' onFinish={handleCreate} onValuesChange={onValuesChange} style={{ marginTop: '-20px' }}>
          <Tabs activeKey={activeTabKey} onChange={setActiveTabKey} type='card'>
            <TabPane tab='Dataset Configuration' key='1'>
              <Row gutter={12}>
                {/* LEFT SIDE FORM INPUTS */}
                <Col span={12}>
                  {/* NAME */}
                  <Form.Item
                    name='name'
                    label={
                      <span>
                        Name <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    rules={[{ message: 'Please input configuration name.' }, { validator: validateFiveChar }]}
                    style={{ marginBottom: 10 }}>
                    <Input placeholder='Enter name' />
                  </Form.Item>

                  {/* CONNECTION */}
                  <Form.Item
                    label={
                      <span>
                        Select Connection <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='selectedConnection'
                    rules={[{ message: 'Please select a connection.' }]}
                    style={{ marginBottom: 10 }}>
                    <Dropdown overlay={connectionMenu} trigger={['click']}>
                      <div
                        style={{
                          border: '1px solid #d9d9d9',
                          padding: '8px 16px',
                          backgroundColor: '#ffffff',
                          cursor: 'pointer',
                          height: 40,
                          display: 'flex',
                          alignItems: 'center',
                        }}>
                        <span>{selectedConnection?.name || `${connections.length} connection(s) found`}</span>
                      </div>
                    </Dropdown>
                  </Form.Item>

                  <Form.Item
                    label={
                      <span>
                        Schema Name <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='table_schema'
                    rules={[{ message: 'Please input schema name.' }]}
                    style={{ marginBottom: 10 }}>
                    <Input placeholder='Enter schema name' value={form.getFieldValue('table_schema')} onChange={(e) => form.setFieldsValue({ table_schema: e.target.value })} disabled={selectedConnection?.type === 'Elasticsearch' || schemaDisabled} />
                  </Form.Item>

                  {/* INDEX / TABLE NAME */}
                  <Form.Item
                    label={
                      <span>
                        {selectedConnection?.type === 'Elasticsearch' ? 'Index Name(s)' : 'Table/View Name(s)'} <span style={{ color: 'red' }}>*</span>
                      </span>
                    }
                    name='table_name'
                    rules={[
                      {
                        message: `Please input ${selectedConnection?.type === 'Elasticsearch' ? 'Index' : 'Table/View name(s)'}.`,
                      },
                    ]}
                    style={{ marginBottom: 10 }}>
                    <Input.TextArea
                      rows={3}
                      placeholder={selectedConnection?.type === 'Elasticsearch' ? 'Enter comma-separated index names, or * for all' : 'Enter comma-separated table/view names, or * for auto selection'}
                      style={{
                        border: '1px solid #d9d9d9',
                        resize: 'none',
                        boxShadow: 'none',
                        padding: '4px 8px',
                      }}
                    />
                  </Form.Item>

                  {/* helper tip kept outside Form.Item, so binding works */}
                  <div
                    style={{
                      backgroundColor: '#e6f4ff',
                      color: '#1677ff',
                      fontSize: '12px',
                      padding: '2px 6px',
                      borderRadius: 4,
                      marginTop: -8,
                      marginBottom: 10,
                      textAlign: 'left',
                    }}>
                    Providing specific {selectedConnection?.type === 'Elasticsearch' ? 'indices' : 'table or view names'} yields more accurate results.
                  </div>
                </Col>

                {/* RIGHT SIDE: TAGS + CONNECTION DETAILS */}
                <Col span={12}>
                  {/* TAGS */}
                  <Form.Item name='tags' label='Tags' style={{ marginBottom: 10 }}>
                    <Select
                      mode='multiple'
                      placeholder='Select tags'
                      allowClear
                      value={selectedTags}
                      onChange={(value) => setSelectedTags(value)}
                      showSearch
                      optionFilterProp='children'
                      filterOption={(input, option) => option?.children?.toLowerCase().includes(input.toLowerCase())}>
                      {Array.isArray(tags) &&
                        tags.map((tag) => (
                          <Option key={tag.id} value={tag.id}>
                            {tag.name}
                          </Option>
                        ))}
                    </Select>
                  </Form.Item>

                  {/* CONNECTION DETAILS */}
                  <label style={{ marginBottom: 7, display: 'block' }}>Connection Details</label>
                  {selectedConnection ? (
                    (() => {
                      const connectionData = JSON.parse(selectedConnection.data || '{}');
                      return (
                        <div
                          style={{
                            borderRadius: 6,
                            height: 260, // fixed height
                            display: 'flex',
                            flexDirection: 'column',
                          }}>
                          <Descriptions bordered column={1} size='small' labelStyle={{ fontWeight: 500 }} style={{ flex: 1, overflowY: 'auto' }}>
                            <Descriptions.Item label='Database Type'>{connectionData.db_type}</Descriptions.Item>

                            {/* Databricks layout */}
                            {connectionData.db_type === 'Databricks' ? (
                              <>
                                <Descriptions.Item label='Host'>{connectionData.host || '-'}</Descriptions.Item>

                                <Descriptions.Item label='HTTP Path'>{connectionData.http_path || '-'}</Descriptions.Item>

                                <Descriptions.Item label='Access Token'>
                                  {connectionData.access_token
                                    ? '***************' // masked for security
                                    : '-'}
                                </Descriptions.Item>

                                <Descriptions.Item label='Catalog'>{connectionData.catalog || '-'}</Descriptions.Item>
                              </>
                            ) : (
                              /* Default layout for all other DBs */
                              <>
                                <Descriptions.Item label='Host'>{connectionData.host || '-'}</Descriptions.Item>

                                <Descriptions.Item label='Port'>{connectionData.port || '-'}</Descriptions.Item>

                                {selectedConnection?.type !== 'Elasticsearch' && <Descriptions.Item label='Database Name'>{connectionData.db_name || '-'}</Descriptions.Item>}

                                <Descriptions.Item label='Username'>{connectionData.user_name || '-'}</Descriptions.Item>

                                {connectionData.db_type === 'Elasticsearch' && <Descriptions.Item label='Secure Connection'>{connectionData.is_secure ? 'Yes' : 'No'}</Descriptions.Item>}
                              </>
                            )}
                          </Descriptions>
                        </div>
                      );
                    })()
                  ) : (
                    <div
                      style={{
                        border: '1px solid #d9d9d9',
                        padding: '16px',
                        background: '#fafafa',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        textAlign: 'center',
                        height: 260,
                      }}>
                      Please select a connection.
                    </div>
                  )}
                </Col>
              </Row>
            </TabPane>

            <TabPane tab='Custom Prompt' key='2'>
              <Form.Item
                label={
                  <div
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      whiteSpace: 'nowrap',
                    }}>
                    <span>
                      Custom Prompt&nbsp;
                      <Tooltip title='Enter a custom prompt with instructions and relevant context.' placement='bottom'>
                        <InfoCircleOutlined />
                      </Tooltip>
                    </span>
                  </div>
                }
                name='custom_prompt'
                style={{ marginBottom: 10 }}>
                <TextArea
                  style={{
                    height: '325px',
                    resize: 'none',
                    overflowY: 'auto',
                  }}
                  placeholder={defaultPrompt}
                />
              </Form.Item>
              <Modal
                title={
                  <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <span style={{ fontWeight: 500 }}>Default Prompt</span>
                    <Tooltip title={copied ? 'Copied' : 'Copy to clipboard'}>
                      <Button
                        type='text'
                        icon={copied ? <CheckCircleOutlined /> : <CopyOutlined />}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleCopy();
                        }}
                        style={{ padding: 0 }}
                      />
                    </Tooltip>
                  </div>
                }
                visible={isPromptModalVisible}
                onCancel={handlePromptModalCancel}
                footer={null}
                width={700}
                style={{ transition: 'none', top: '8%' }}
                bodyStyle={{ padding: 10, paddingTop: 0, paddingBottom: 15, maxHeight: '380px', overflowY: 'auto' }}>
                <div id='formattedPromptHTML' style={{ lineHeight: 1.4 }} dangerouslySetInnerHTML={{ __html: formattedPrompt }} />
              </Modal>
            </TabPane>

            <TabPane tab='Examples' key='3'>
              <Form.Item style={{ marginBottom: 10 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}>
                  <div>
                    <span>
                      Examples&nbsp;
                      <Tooltip title="Add example queries to improve the quality of the LLM's results." placement='bottom'>
                        <InfoCircleOutlined />
                      </Tooltip>
                    </span>
                  </div>

                  <Space>
                    <ExamplesJsonImportExport form={form} showNotification={showNotification} onExamplesChange={onValuesChange} mode='examples' />
                    <Button type='primary' onClick={() => showSqlAddModal()}>
                      Add
                    </Button>
                  </Space>
                </div>
              </Form.Item>

              <div
                style={{
                  height: '308px',
                  overflowY: 'auto',
                  paddingRight: '4px',
                  marginBottom: '15px',
                }}>
                <Form.List name='examples'>
                  {(fields) => {
                    const examples = form.getFieldValue('examples') || [];

                    if (examples.length === 0) {
                      return (
                        <div
                          style={{
                            textAlign: 'center',
                            color: '#999',
                            padding: '15% 0',
                          }}>
                          No Examples added.
                        </div>
                      );
                    }

                    return examples.map((item, index) => (
                      <div
                        key={index}
                        style={{
                          border: '1px solid #e5e5e5',
                          borderRadius: 8,
                          padding: 5,
                          paddingLeft: 10,
                          marginBottom: 10,
                          position: 'relative',
                        }}>
                        {/* Question text */}
                        <div
                          style={{
                            cursor: 'pointer',
                            maxWidth: '760px',
                            overflow: 'hidden',
                            whiteSpace: 'nowrap',
                            textOverflow: 'ellipsis',
                            paddingRight: '150px',
                          }}
                          onClick={() => showSqlViewModal(index)}>
                          {item.key}
                        </div>

                        {/* RIGHT-SIDE ACTION + TAG */}
                        <div
                          style={{
                            position: 'absolute',
                            top: 5,
                            right: 10,
                            display: 'flex',
                            gap: 8,
                            alignItems: 'center',
                          }}>
                          {/* Example Type Tag */}
                          <Tag color={item.type === 'Core' ? 'green' : 'blue'} style={{ fontSize: 11 }}>
                            {item.type === 'Core' ? 'Core' : 'Semantic Search'}
                          </Tag>

                          <Button
                            size='small'
                            icon={<EditOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              showSqlAddModal(index);
                            }}
                          />

                          <Button
                            size='small'
                            icon={<DeleteOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSqlDelete(index);
                            }}
                          />
                        </div>
                      </div>
                    ));
                  }}
                </Form.List>
              </div>
              <Modal title={sqlExampleModalMode === 'edit' ? 'Edit Example' : sqlExampleModalMode === 'view' ? 'View SQL Example' : 'Add Example'} open={isModalVisible} onCancel={() => setIsModalVisible(false)} footer={null}>
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
                        }
                        disabled={sqlExampleModalMode === 'view'}>
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
                      readOnly={sqlExampleModalMode === 'view'}
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
                        if (selectedConnection?.type !== 'Elasticsearch') {
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

                  {sqlExampleModalMode !== 'view' && (
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
                  )}
                </div>
              </Modal>
            </TabPane>

            <TabPane tab='Business Context' key='4'>
              <Form.Item
                label={
                  <div>
                    <span>
                      Business Context&nbsp;
                      <Tooltip title='Provide business-friendly names for internal codes or terms.' placement='bottom'>
                        <InfoCircleOutlined />
                      </Tooltip>
                    </span>
                  </div>
                }
                name='business_rules'
                style={{ marginBottom: 10 }}>
                <TextArea
                  style={{
                    height: '325px',
                    resize: 'none',
                    overflowY: 'auto',
                  }}
                  autoSize={false}
                />
              </Form.Item>
            </TabPane>

            <TabPane tab='Sample Questions' key='5'>
              <Form.Item style={{ marginBottom: 10 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}>
                  <div>
                    <span>
                      Sample Questions&nbsp;
                      <Tooltip title='Add sample questions to be displayed by default while chatting.' placement='bottom'>
                        <InfoCircleOutlined />
                      </Tooltip>
                    </span>
                  </div>

                  <Space>
                    <ExamplesJsonImportExport form={form} showNotification={showNotification} onExamplesChange={isCreateModalVisible ? onValuesChange : onValuesChangeEdit} mode='sampleQuestions' />
                    <Button type='primary' onClick={() => showSampleQuestionAddModal()}>
                      Add
                    </Button>
                  </Space>
                </div>
              </Form.Item>

              <div
                style={{
                  height: '308px',
                  overflowY: 'auto',
                  paddingRight: '4px',
                  marginBottom: '15px',
                }}>
                <Form.List name='sample_question'>
                  {(fields) => {
                    const examples = form.getFieldValue('sample_question') || [];

                    if (examples.length === 0) {
                      return (
                        <div
                          style={{
                            textAlign: 'center',
                            color: '#999',
                            padding: '15% 0',
                          }}>
                          No Sample questions added.
                        </div>
                      );
                    }

                    return examples.map((item, index) => (
                      <div
                        key={index}
                        style={{
                          border: '1px solid #e5e5e5',
                          borderRadius: 8,
                          padding: 5,
                          paddingLeft: 10,
                          marginBottom: 10,
                          position: 'relative',
                        }}>
                        <div
                          style={{
                            cursor: 'pointer',
                            maxWidth: '800px',
                            overflow: 'hidden',
                            whiteSpace: 'nowrap',
                            textOverflow: 'ellipsis',
                            paddingRight: '80px',
                          }}
                          onClick={() => showSampleQuestionViewModal(index)}>
                          {item.key}
                        </div>

                        <div
                          style={{
                            position: 'absolute',
                            top: 5,
                            right: 10,
                            display: 'flex',
                            gap: 8,
                          }}>
                          <Button
                            size='small'
                            icon={<EditOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              showSampleQuestionAddModal(index);
                            }}
                          />
                          <Button
                            size='small'
                            icon={<DeleteOutlined />}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSampleQuestionDelete(index);
                            }}
                          />
                        </div>
                      </div>
                    ));
                  }}
                </Form.List>
              </div>
              <Modal
                title={sampleQuestionModalMode === 'edit' ? 'Edit Sample Question' : sampleQuestionModalMode === 'view' ? 'View Sample Question' : 'Add Sample Question'}
                open={isSampleQuestionModalVisible}
                onCancel={() => {
                  setIsSampleQuestionModalVisible(false);
                  setSampleQuestionModalMode('add');
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
                    Sample Question
                    <Input.TextArea
                      rows={3}
                      value={sampleQuestionModalData.sample_question}
                      onChange={(e) =>
                        setSampleQuestionModalData({
                          sample_question: e.target.value,
                        })
                      }
                      placeholder='Enter the sample question'
                      style={{ resize: 'none', marginTop: '2px' }}
                      readOnly={sampleQuestionModalMode === 'view'}
                    />
                  </label>

                  {sampleQuestionModalMode !== 'view' && (
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'flex-end',
                        gap: 8,
                        marginTop: -12,
                      }}>
                      <Button
                        onClick={() => {
                          setIsSampleQuestionModalVisible(false);
                          setSampleQuestionModalMode('add'); // reset mode on cancel
                        }}>
                        Cancel
                      </Button>
                      <Button type='primary' onClick={handleSampleQuestionAdd} disabled={!sampleQuestionModalData.sample_question.trim()}>
                        OK
                      </Button>
                    </div>
                  )}
                </div>
              </Modal>
            </TabPane>
          </Tabs>
          <div
            className='button-container'
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              marginTop: 10,
            }}>
            {/* Left side: Insert Placeholder button only for Custom Prompt tab */}
            {activeTabKey === '2' ? (
              <div style={{ display: 'flex', gap: '8px' }}>
                <Button onClick={showPromptModal} type='primary'>
                  Default Prompt
                </Button>
                <Button type='primary' onClick={fetchMacros}>
                  Prompt Macros
                </Button>
              </div>
            ) : (
              <div /> // Keeps spacing on the left for other tabs
            )}

            {/* Right side: Save button always visible */}
            <Space>
              <Button
                type='primary'
                disabled={btndisabled}
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

      <Modal title='Update Bot Configuration' visible={isEditModalVisible} onCancel={handleEditCancel} footer={null} style={{ top: '5%', transition: 'none' }} afterClose={() => form.resetFields()} width={1000}>
        {updateModalLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '445px' }}>
            <LoadingWidget />
          </div>
        ) : (
          <Form form={form} layout='vertical' size='middle' onFinish={handleUpdate} onValuesChange={onValuesChangeEdit} style={{ marginTop: '-20px' }}>
            <Tabs activeKey={activeTabKey} onChange={setActiveTabKey} type='card'>
              <TabPane tab='Dataset Configuration' key='1'>
                <Row gutter={12}>
                  {/* LEFT SIDE FORM INPUTS */}
                  <Col span={12}>
                    {/* NAME */}
                    <Form.Item
                      name='name'
                      label={
                        <span>
                          Name <span style={{ color: 'red' }}>*</span>
                        </span>
                      }
                      rules={[{ message: 'Please input configuration name.' }, { validator: validateFiveChar }]}
                      style={{ marginBottom: 10 }}>
                      <Input placeholder='Enter name' />
                    </Form.Item>

                    {/* CONNECTION */}
                    <Form.Item
                      label={
                        <span>
                          Select Connection <span style={{ color: 'red' }}>*</span>
                        </span>
                      }
                      name='selectedConnection'
                      rules={[{ message: 'Please select a connection.' }]}
                      style={{ marginBottom: 10 }}>
                      <Dropdown overlay={connectionMenu} trigger={['click']}>
                        <div
                          style={{
                            border: '1px solid #d9d9d9',
                            padding: '8px 16px',
                            backgroundColor: '#ffffff',
                            cursor: 'pointer',
                            height: 40,
                            display: 'flex',
                            alignItems: 'center',
                          }}>
                          <span>{selectedConnection?.name || `${connections.length} connection(s) found`}</span>
                        </div>
                      </Dropdown>
                    </Form.Item>

                    <Form.Item
                      label={
                        <span>
                          Schema Name <span style={{ color: 'red' }}>*</span>
                        </span>
                      }
                      name='table_schema'
                      rules={[{ message: 'Please input schema name.' }]}
                      style={{ marginBottom: 10 }}>
                      <Input
                        placeholder='Enter schema name'
                        value={form.getFieldValue('table_schema')}
                        onChange={(e) => form.setFieldsValue({ table_schema: e.target.value })}
                        disabled={selectedConnection?.type === 'Elasticsearch' || schemaDisabled}
                      />
                    </Form.Item>

                    {/* INDEX / TABLE NAME */}
                    <Form.Item
                      label={
                        <span>
                          {selectedConnection?.type === 'Elasticsearch' ? 'Index Name(s)' : 'Table/View Name(s)'} <span style={{ color: 'red' }}>*</span>
                        </span>
                      }
                      name='table_name'
                      rules={[
                        {
                          message: `Please input ${selectedConnection?.type === 'Elasticsearch' ? 'Index' : 'Table/View name(s)'}.`,
                        },
                      ]}
                      style={{ marginBottom: 10 }}>
                      <Input.TextArea
                        rows={3}
                        placeholder={selectedConnection?.type === 'Elasticsearch' ? 'Enter comma-separated index names, or * for all' : 'Enter comma-separated table/view names, or * for auto selection'}
                        style={{
                          border: '1px solid #d9d9d9',
                          resize: 'none',
                          boxShadow: 'none',
                          padding: '4px 8px',
                        }}
                      />
                    </Form.Item>

                    {/* helper text separately, not wrapping the Input */}
                    <div
                      style={{
                        backgroundColor: '#e6f4ff',
                        color: '#1677ff',
                        fontSize: '12px',
                        padding: '2px 6px',
                        borderRadius: 4,
                        marginTop: -8,
                        marginBottom: 10,
                        textAlign: 'left',
                      }}>
                      Providing specific {selectedConnection?.type === 'Elasticsearch' ? 'indices' : 'table or view names'} yields more accurate results.
                    </div>
                  </Col>

                  {/* RIGHT SIDE: TAGS + CONNECTION DETAILS */}
                  <Col span={12}>
                    {/* TAGS */}
                    <Form.Item name='tags' label='Tags' style={{ marginBottom: 10 }}>
                      <Select
                        mode='multiple'
                        placeholder='Select tags'
                        allowClear
                        value={selectedTags}
                        onChange={(value) => setSelectedTags(value)}
                        showSearch
                        optionFilterProp='children'
                        filterOption={(input, option) => option?.children?.toLowerCase().includes(input.toLowerCase())}>
                        {Array.isArray(tags) &&
                          tags.map((tag) => (
                            <Option key={tag.id} value={tag.id}>
                              {tag.name}
                            </Option>
                          ))}
                      </Select>
                    </Form.Item>

                    {/* CONNECTION DETAILS */}
                    <label style={{ marginBottom: 7, display: 'block' }}>Connection Details</label>
                    {selectedConnection ? (
                      (() => {
                        const connectionData = JSON.parse(selectedConnection.data || '{}');
                        return (
                          <div
                            style={{
                              borderRadius: 6,
                              height: 260, // fixed height
                              display: 'flex',
                              flexDirection: 'column',
                            }}>
                            <Descriptions bordered column={1} size='small' labelStyle={{ fontWeight: 500 }} style={{ flex: 1, overflowY: 'auto' }}>
                              <Descriptions.Item label='Database Type'>{connectionData.db_type}</Descriptions.Item>

                              {/* Databricks layout */}
                              {connectionData.db_type === 'Databricks' ? (
                                <>
                                  <Descriptions.Item label='Host'>{connectionData.host || '-'}</Descriptions.Item>

                                  <Descriptions.Item label='HTTP Path'>{connectionData.http_path || '-'}</Descriptions.Item>

                                  <Descriptions.Item label='Access Token'>{connectionData.access_token ? '***************' : '-'}</Descriptions.Item>

                                  <Descriptions.Item label='Catalog'>{connectionData.catalog || '-'}</Descriptions.Item>
                                </>
                              ) : (
                                /* Default layout for all other DBs */
                                <>
                                  <Descriptions.Item label='Host'>{connectionData.host || '-'}</Descriptions.Item>

                                  <Descriptions.Item label='Port'>{connectionData.port || '-'}</Descriptions.Item>

                                  {selectedConnection?.type !== 'Elasticsearch' && <Descriptions.Item label='Database Name'>{connectionData.db_name || '-'}</Descriptions.Item>}

                                  <Descriptions.Item label='Username'>{connectionData.user_name || '-'}</Descriptions.Item>

                                  {connectionData.db_type === 'Elasticsearch' && <Descriptions.Item label='Secure Connection'>{connectionData.is_secure ? 'Yes' : 'No'}</Descriptions.Item>}
                                </>
                              )}
                            </Descriptions>
                          </div>
                        );
                      })()
                    ) : (
                      <div
                        style={{
                          border: '1px solid #d9d9d9',
                          padding: '16px',
                          background: '#fafafa',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          textAlign: 'center',
                          height: 260,
                        }}>
                        Please select a connection.
                      </div>
                    )}
                  </Col>
                </Row>
              </TabPane>

              <TabPane tab='Custom Prompt' key='2'>
                <Form.Item
                  label={
                    <div
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        whiteSpace: 'nowrap',
                      }}>
                      <span>
                        Custom Prompt&nbsp;
                        <Tooltip title='Enter a custom prompt with instructions and relevant context.' placement='bottom'>
                          <InfoCircleOutlined />
                        </Tooltip>
                      </span>
                    </div>
                  }
                  name='custom_prompt'
                  style={{ marginBottom: 10 }}>
                  <TextArea
                    style={{
                      height: '325px',
                      resize: 'none',
                      overflowY: 'auto',
                    }}
                    placeholder={defaultPrompt}
                  />
                </Form.Item>
                <Modal
                  title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <span style={{ fontWeight: 500 }}>Default Prompt</span>
                      <Tooltip title={copied ? 'Copied' : 'Copy to clipboard'}>
                        <Button
                          type='text'
                          icon={copied ? <CheckCircleOutlined /> : <CopyOutlined />}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleCopy();
                          }}
                          style={{ padding: 0 }}
                        />
                      </Tooltip>
                    </div>
                  }
                  visible={isPromptModalVisible}
                  onCancel={handlePromptModalCancel}
                  footer={null}
                  width={700}
                  style={{ transition: 'none' }}
                  bodyStyle={{ padding: 10, paddingTop: 0, paddingBottom: 15, maxHeight: '380px', overflowY: 'auto' }}>
                  <div id='formattedPromptHTML' style={{ lineHeight: 1.4 }} dangerouslySetInnerHTML={{ __html: formattedPrompt }} />
                </Modal>
              </TabPane>

              <TabPane tab='Examples' key='3'>
                <Form.Item style={{ marginBottom: 10 }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}>
                    <div>
                      <span>
                        Examples&nbsp;
                        <Tooltip title="Add example queries to improve the quality of the LLM's results." placement='bottom'>
                          <InfoCircleOutlined />
                        </Tooltip>
                      </span>
                    </div>

                    <Space>
                      <ExamplesJsonImportExport form={form} showNotification={showNotification} onExamplesChange={onValuesChangeEdit} mode='examples' />
                      <Button type='primary' onClick={() => showSqlAddModal()}>
                        Add
                      </Button>
                    </Space>
                  </div>
                </Form.Item>

                <div
                  style={{
                    height: '308px',
                    overflowY: 'auto',
                    paddingRight: '4px',
                    marginBottom: '15px',
                  }}>
                  <Form.List name='examples'>
                    {(fields) => {
                      const examples = form.getFieldValue('examples') || [];

                      if (examples.length === 0) {
                        return (
                          <div
                            style={{
                              textAlign: 'center',
                              color: '#999',
                              padding: '15% 0',
                            }}>
                            No Examples added.
                          </div>
                        );
                      }

                      return examples.map((item, index) => (
                        <div
                          key={index}
                          style={{
                            border: '1px solid #e5e5e5',
                            borderRadius: 8,
                            padding: 5,
                            paddingLeft: 10,
                            marginBottom: 10,
                            position: 'relative',
                          }}>
                          {/* Question text */}
                          <div
                            style={{
                              cursor: 'pointer',
                              maxWidth: '760px',
                              overflow: 'hidden',
                              whiteSpace: 'nowrap',
                              textOverflow: 'ellipsis',
                              paddingRight: '150px',
                            }}
                            onClick={() => showSqlViewModal(index)}>
                            {item.key}
                          </div>

                          {/* RIGHT-SIDE ACTION + TAG */}
                          <div
                            style={{
                              position: 'absolute',
                              top: 5,
                              right: 10,
                              display: 'flex',
                              gap: 8,
                              alignItems: 'center',
                            }}>
                            {/* Example Type Tag */}
                            <Tag color={item.type === 'Core' ? 'green' : 'blue'} style={{ fontSize: 11 }}>
                              {item.type === 'Core' ? 'Core' : 'Semantic Search'}
                            </Tag>

                            <Button
                              size='small'
                              icon={<EditOutlined />}
                              onClick={(e) => {
                                e.stopPropagation();
                                showSqlAddModal(index);
                              }}
                            />

                            <Button
                              size='small'
                              icon={<DeleteOutlined />}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleSqlDelete(index);
                              }}
                            />
                          </div>
                        </div>
                      ));
                    }}
                  </Form.List>
                </div>
                <Modal title={sqlExampleModalMode === 'edit' ? 'Edit Example' : sqlExampleModalMode === 'view' ? 'View Example' : 'Add Example'} open={isModalVisible} onCancel={() => setIsModalVisible(false)} footer={null}>
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
                          }
                          disabled={sqlExampleModalMode === 'view'}>
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
                        readOnly={sqlExampleModalMode === 'view'}
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
                          if (selectedConnection?.type !== 'Elasticsearch') {
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

                    {sqlExampleModalMode !== 'view' && (
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
                    )}
                  </div>
                </Modal>
              </TabPane>

              <TabPane tab='Business Context' key='4'>
                <Form.Item
                  label={
                    <div>
                      <span>
                        Business Context&nbsp;
                        <Tooltip title='Provide business-friendly names for internal codes or terms.' placement='bottom'>
                          <InfoCircleOutlined />
                        </Tooltip>
                      </span>
                    </div>
                  }
                  name='business_rules'
                  style={{ marginBottom: 10 }}>
                  <TextArea
                    style={{
                      height: '325px',
                      resize: 'none',
                      overflowY: 'auto',
                    }}
                    autoSize={false}
                  />
                </Form.Item>
              </TabPane>

              <TabPane tab='Sample Questions' key='5'>
                <Form.Item style={{ marginBottom: 10 }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}>
                    <div>
                      <span>
                        Sample Questions&nbsp;
                        <Tooltip title='Add sample questions to be displayed by default while chatting.' placement='bottom'>
                          <InfoCircleOutlined />
                        </Tooltip>
                      </span>
                    </div>

                    <Space>
                      <ExamplesJsonImportExport form={form} showNotification={showNotification} onExamplesChange={isCreateModalVisible ? onValuesChange : onValuesChangeEdit} mode='sampleQuestions' />
                      <Button type='primary' onClick={() => showSampleQuestionAddModal()}>
                        Add
                      </Button>
                    </Space>
                  </div>
                </Form.Item>

                <div
                  style={{
                    height: '308px',
                    overflowY: 'auto',
                    paddingRight: '4px',
                    marginBottom: '15px',
                  }}>
                  <Form.List name='sample_question'>
                    {(fields) => {
                      const examples = form.getFieldValue('sample_question') || [];

                      if (examples.length === 0) {
                        return (
                          <div
                            style={{
                              textAlign: 'center',
                              color: '#999',
                              padding: '15% 0',
                            }}>
                            No Sample questions added.
                          </div>
                        );
                      }

                      return examples.map((item, index) => (
                        <div
                          key={index}
                          style={{
                            border: '1px solid #e5e5e5',
                            borderRadius: 8,
                            padding: 5,
                            paddingLeft: 10,
                            marginBottom: 10,
                            position: 'relative',
                          }}>
                          <div
                            style={{
                              cursor: 'pointer',
                              maxWidth: '800px',
                              overflow: 'hidden',
                              whiteSpace: 'nowrap',
                              textOverflow: 'ellipsis',
                              paddingRight: '80px',
                            }}
                            onClick={() => showSampleQuestionViewModal(index)}>
                            {item.key}
                          </div>

                          <div
                            style={{
                              position: 'absolute',
                              top: 5,
                              right: 10,
                              display: 'flex',
                              gap: 8,
                            }}>
                            <Button
                              size='small'
                              icon={<EditOutlined />}
                              onClick={(e) => {
                                e.stopPropagation();
                                showSampleQuestionAddModal(index);
                              }}
                            />
                            <Button
                              size='small'
                              icon={<DeleteOutlined />}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleSampleQuestionDelete(index);
                              }}
                            />
                          </div>
                        </div>
                      ));
                    }}
                  </Form.List>
                </div>
                <Modal
                  title={sampleQuestionModalMode === 'edit' ? 'Edit Sample Question' : sampleQuestionModalMode === 'view' ? 'View Sample Question' : 'Add Sample Question'}
                  open={isSampleQuestionModalVisible}
                  onCancel={() => {
                    setIsSampleQuestionModalVisible(false);
                    setSampleQuestionModalMode('add');
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
                      Sample Question
                      <Input.TextArea
                        rows={3}
                        value={sampleQuestionModalData.sample_question}
                        onChange={(e) =>
                          setSampleQuestionModalData({
                            sample_question: e.target.value,
                          })
                        }
                        placeholder='Enter the sample question'
                        style={{ resize: 'none', marginTop: '2px' }}
                        readOnly={sampleQuestionModalMode === 'view'}
                      />
                    </label>

                    {sampleQuestionModalMode !== 'view' && (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'flex-end',
                          gap: 8,
                          marginTop: -12,
                        }}>
                        <Button
                          onClick={() => {
                            setIsSampleQuestionModalVisible(false);
                            setSampleQuestionModalMode('add'); // reset mode on cancel
                          }}>
                          Cancel
                        </Button>
                        <Button type='primary' onClick={handleSampleQuestionAdd} disabled={!sampleQuestionModalData.sample_question.trim()}>
                          OK
                        </Button>
                      </div>
                    )}
                  </div>
                </Modal>
              </TabPane>
            </Tabs>
            <div
              className='button-container'
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                marginTop: 10,
              }}>
              {/* Left side: Insert Placeholder button only for Custom Prompt tab */}
              {activeTabKey === '2' ? (
                <div style={{ display: 'flex', gap: '8px' }}>
                  <Button onClick={showPromptModal} type='primary'>
                    Default Prompt
                  </Button>
                  <Button type='primary' onClick={fetchMacros}>
                    Prompt Macros
                  </Button>
                </div>
              ) : (
                <div /> // Keeps spacing on the left for other tabs
              )}

              {/* Right side: Save button always visible */}
              <Space>
                <Button
                  disabled={isEditButtonDisabled}
                  type='primary'
                  onClick={() => {
                    form.submit();
                    setTimeout(() => {
                      setIsEditModalVisible(false); // Close modal immediately after submitting
                    }, 0); // Set a minimal timeout to prevent modal closing delay
                  }}>
                  Update
                </Button>
              </Space>
            </div>
          </Form>
        )}
      </Modal>

      {/* Macros MODAL */}
      <Modal title='Prompt Macros' visible={isMacroModalVisible} onCancel={() => setIsMacroModalVisible(false)} footer={null} zIndex={1100} width={700} bodyStyle={{ paddingTop: 0 }}>
        {macros.length === 0 ? (
          <Empty description='No macros found.' />
        ) : (
          <div style={{ maxHeight: 300, overflowY: 'auto' }}>
            <Table
              dataSource={macros}
              pagination={false}
              rowKey='id'
              size='small'
              columns={[
                {
                  title: 'Name',
                  dataIndex: 'name',
                  key: 'name',
                  width: '25%',
                  render: (text) => <Text>{text}</Text>,
                },
                {
                  title: 'Description',
                  dataIndex: 'description',
                  key: 'description',
                  width: '75%',
                  render: (text) => <Text>{text}</Text>,
                },
              ]}
            />
          </div>
        )}
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

export default ProductInfo;
