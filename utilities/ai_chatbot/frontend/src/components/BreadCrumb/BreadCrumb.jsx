import { Breadcrumb } from 'antd';
import { HomeOutlined } from '@ant-design/icons';

const PageBreadcrumb = ({ title }) => {
  return (
    <div style={{ paddingTop: '5px', paddingLeft: '15px' }}>
      <Breadcrumb separator=">">
        <Breadcrumb.Item href="/">
          <HomeOutlined />
        </Breadcrumb.Item>
        <Breadcrumb.Item>{title}</Breadcrumb.Item>
      </Breadcrumb>
    </div>
  );
};

export default PageBreadcrumb;
