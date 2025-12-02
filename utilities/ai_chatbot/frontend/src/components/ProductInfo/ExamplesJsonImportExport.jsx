import React, { useRef, useState } from 'react';
import { Button, Space, Tooltip } from 'antd';
import { DownloadOutlined, UploadOutlined, CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';

const ExamplesJsonImportExport = ({
  form,
  showNotification,
  onExamplesChange,
  mode = 'examples', // 'examples' | 'sampleQuestions'
}) => {
  const fileInputRef = useRef(null);
  const [isExporting, setIsExporting] = useState(false);
  const [isImporting, setIsImporting] = useState(false);

  const isExamplesMode = mode === 'examples';

  const fieldName = isExamplesMode ? 'examples' : 'sample_question';
  const fileName = isExamplesMode ? 'examples.json' : 'sample_questions.json';

  const labelPlural = isExamplesMode ? 'examples' : 'sample questions';
  const labelTitle = isExamplesMode ? 'Examples' : 'Sample Questions';

  const exportTooltip = `Export ${labelPlural}`;
  const importTooltip = `Import ${labelPlural}`;

  const normalizeJson = (rawJson) => {
    let parsed;
    try {
      parsed = JSON.parse(rawJson);
    } catch (e) {
      throw new Error('Invalid JSON. Please check the syntax.');
    }

    if (!Array.isArray(parsed)) {
      throw new Error('JSON must be an array.');
    }

    const seenQuestions = new Set();

    return parsed.map((ex, idx) => {
      const question = ex.question ?? ex.key ?? ex.detail;

      if (!question) {
        if (isExamplesMode) {
          throw new Error(`Item at index ${idx} is not a valid example. Each example must have "question" (or "key") and "query" (or "value").`);
        } else {
          throw new Error(`Item at index ${idx} is missing "question"/"key"/"detail".`);
        }
      }

      const normalizedQuestion = String(question).trim();
      const questionKey = normalizedQuestion.toLowerCase();

      if (seenQuestions.has(questionKey)) {
        throw new Error(`Duplicate question "${normalizedQuestion}" found in JSON (at index ${idx}). Please remove duplicates and try again.`);
      }
      seenQuestions.add(questionKey);

      if (isExamplesMode) {
        const rawQuery = ex.query ?? ex.value;

        if (!rawQuery) {
          throw new Error(`Item at index ${idx} is not a valid example. Each example must have "question" (or "key") and "query" (or "value").`);
        }

        if (typeof rawQuery !== 'string') {
          throw new Error(`Query for item "${normalizedQuestion}" must be a string. Objects/JSON queries are not allowed.`);
        }

        const normalizedQuery = rawQuery.trim();

        return {
          id: ex.id ?? -1,
          key: normalizedQuestion,
          value: normalizedQuery,
          type: ex.type === 'Core' ? 'Core' : 'Semantic',
        };
      } else {
        // sampleQuestions mode

        // HARD CHECK: looks like Examples JSON
        if (typeof ex.query !== 'undefined' || typeof ex.value !== 'undefined') {
          throw new Error(`Item at index ${idx} looks like an Example (has "query"/"value"). ` + 'Please import this file in the Examples tab instead of Sample Questions.');
        }

        const detail = ex.detail ?? normalizedQuestion;

        return {
          id: ex.id ?? -1,
          key: normalizedQuestion,
          detail: String(detail).trim(),
        };
      }
    });
  };

  const handleExport = () => {
    const items = form.getFieldValue(fieldName) || [];

    if (!items.length) {
      if (showNotification) {
        showNotification(<InfoCircleOutlined style={{ color: '#1890ff' }} />, `No ${labelTitle}`, `There are no ${labelPlural} to export.`);
      }
      return;
    }

    try {
      setIsExporting(true);

      const exportPayload = isExamplesMode
        ? // EXAMPLES: DO NOT export id
          items.map((ex) => ({
            question: ex.key,
            query: ex.value,
            type: ex.type || '',
          }))
        : // SAMPLE QUESTIONS: DO NOT export id
          items.map((q) => ({
            question: q.key,
          }));

      const blob = new Blob([JSON.stringify(exportPayload, null, 2)], {
        type: 'application/json;charset=utf-8',
      });

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');

      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      if (showNotification) {
        showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Export Failed', `Unable to export ${labelPlural}.`);
      }
    } finally {
      setIsExporting(false);
    }
  };

  const handleImportClick = () => {
    if (isImporting) return;
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      fileInputRef.current.click();
    }
  };

  const handleFileChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    setIsImporting(true);

    reader.onload = () => {
      try {
        const text = reader.result;
        const normalized = normalizeJson(text); // validated & duplicate-free within file

        const existingItems = form.getFieldValue(fieldName) || [];
        const existingQuestionSet = new Set(existingItems.map((item) => (item.key ?? '').toString().trim().toLowerCase()));

        // Prevent duplicates across existing + new
        for (const item of normalized) {
          const qKey = (item.key ?? '').toString().trim().toLowerCase();
          if (existingQuestionSet.has(qKey)) {
            throw new Error(`Question "${item.key}" already exists in current ${labelPlural}. ` + 'Please remove duplicates before importing.');
          }
        }

        const merged = [...existingItems, ...normalized];

        form.setFieldsValue({ [fieldName]: merged });

        if (typeof onExamplesChange === 'function') {
          onExamplesChange();
        }

        if (showNotification) {
          showNotification(<CheckCircleOutlined style={{ color: 'green' }} />, 'Imported', `Successfully imported ${normalized.length} new ${labelPlural}.`);
        }
      } catch (err) {
        if (showNotification) {
          showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Import Failed', err?.message || `Unable to import ${labelPlural} from JSON.`);
        }
      } finally {
        setIsImporting(false);
      }
    };

    reader.onerror = () => {
      if (showNotification) {
        showNotification(<CloseCircleOutlined style={{ color: 'red' }} />, 'Import Failed', 'Could not read the selected file.');
      }
      setIsImporting(false);
    };

    reader.readAsText(file);
  };

  return (
    <>
      <input ref={fileInputRef} type='file' accept='application/json,.json' style={{ display: 'none' }} onChange={handleFileChange} />
      <Space>
        <Tooltip title={exportTooltip}>
          <Button icon={<UploadOutlined />} onClick={handleExport} loading={isExporting} disabled={isImporting} />
        </Tooltip>

        <Tooltip title={importTooltip}>
          <Button icon={<DownloadOutlined />} onClick={handleImportClick} loading={isImporting} disabled={isExporting} />
        </Tooltip>
      </Space>
    </>
  );
};

export default ExamplesJsonImportExport;
