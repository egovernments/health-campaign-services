#!/usr/bin/env python3
"""
Google Drive Uploader Module
Uploads files to Google Drive using Service Account authentication
"""

import os
import json
import logging
from pathlib import Path
from typing import Optional, Dict, List
from datetime import datetime

logger = logging.getLogger(__name__)


class GoogleDriveUploader:
    """Upload files to Google Drive using Service Account"""

    def __init__(self):
        """Initialize Google Drive uploader with service account credentials"""
        try:
            from google.oauth2 import service_account
            from googleapiclient.discovery import build
            from googleapiclient.http import MediaFileUpload

            self.service_account = service_account
            self.build = build
            self.MediaFileUpload = MediaFileUpload
        except ImportError:
            raise ImportError(
                "Google Drive dependencies not installed. "
                "Install with: pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib"
            )

        # Get configuration from environment
        self.credentials_path = os.getenv('GOOGLE_DRIVE_CREDENTIALS_PATH', '/app/secrets/google_drive_credentials.json')
        self.folder_id = os.getenv('GOOGLE_DRIVE_FOLDER_ID')  # Optional: specific folder to upload to
        self.share_with_emails = os.getenv('GOOGLE_DRIVE_SHARE_WITH', '').split(',') if os.getenv('GOOGLE_DRIVE_SHARE_WITH') else []

        # Validate credentials file exists
        if not os.path.exists(self.credentials_path):
            raise FileNotFoundError(
                f"Google Drive credentials file not found: {self.credentials_path}\n"
                f"Please create a Service Account and download credentials JSON"
            )

        # Initialize Google Drive service
        self.service = self._initialize_service()
        logger.info("✅ Google Drive uploader initialized successfully")

    def _initialize_service(self):
        """Initialize Google Drive API service"""
        try:
            # Load service account credentials
            credentials = self.service_account.Credentials.from_service_account_file(
                self.credentials_path,
                scopes=['https://www.googleapis.com/auth/drive.file']
            )

            # Build Drive API service
            service = self.build('drive', 'v3', credentials=credentials)

            # Test connection by getting about info
            about = service.about().get(fields='user').execute()
            logger.info(f"Connected to Google Drive as: {about.get('user', {}).get('emailAddress', 'Service Account')}")

            return service

        except Exception as e:
            logger.error(f"Failed to initialize Google Drive service: {e}")
            raise

    def upload_file(
        self,
        local_file_path: str,
        remote_filename: Optional[str] = None,
        folder_id: Optional[str] = None,
        share_with: Optional[List[str]] = None
    ) -> Dict:
        """
        Upload a file to Google Drive

        Args:
            local_file_path: Path to local file to upload
            remote_filename: Name for file in Google Drive (defaults to local filename)
            folder_id: Google Drive folder ID to upload to (overrides env var)
            share_with: List of email addresses to share file with (overrides env var)

        Returns:
            Dict with upload result:
            {
                'success': bool,
                'file_id': str,
                'web_view_link': str,
                'web_content_link': str,
                'error': str (if failed)
            }
        """
        try:
            # Validate file exists
            if not os.path.exists(local_file_path):
                return {
                    'success': False,
                    'error': f"File not found: {local_file_path}"
                }

            # Get file info
            file_size = os.path.getsize(local_file_path)
            if remote_filename is None:
                remote_filename = os.path.basename(local_file_path)

            logger.info(f"Uploading {remote_filename} ({file_size / (1024*1024):.2f} MB) to Google Drive...")

            # Determine MIME type
            mime_type = self._get_mime_type(local_file_path)

            # Prepare file metadata
            file_metadata = {
                'name': remote_filename,
                'mimeType': mime_type
            }

            # Add to specific folder if provided
            target_folder_id = folder_id or self.folder_id
            if target_folder_id:
                file_metadata['parents'] = [target_folder_id]
                logger.info(f"Uploading to folder ID: {target_folder_id}")

            # Create media upload
            media = self.MediaFileUpload(
                local_file_path,
                mimetype=mime_type,
                resumable=True
            )

            # Upload file
            file = self.service.files().create(
                body=file_metadata,
                media_body=media,
                fields='id, name, webViewLink, webContentLink, mimeType, size'
            ).execute()

            file_id = file.get('id')
            web_view_link = file.get('webViewLink')
            web_content_link = file.get('webContentLink')

            logger.info(f"✅ File uploaded successfully!")
            logger.info(f"   File ID: {file_id}")
            logger.info(f"   View Link: {web_view_link}")
            logger.info(f"   Download Link: {web_content_link}")

            # Share file with specific users if requested
            share_emails = share_with or self.share_with_emails
            if share_emails:
                self._share_file(file_id, share_emails)

            return {
                'success': True,
                'file_id': file_id,
                'file_name': file.get('name'),
                'web_view_link': web_view_link,
                'web_content_link': web_content_link,
                'mime_type': file.get('mimeType'),
                'size': file.get('size')
            }

        except Exception as e:
            error_msg = f"Failed to upload file: {str(e)}"
            logger.error(f"❌ {error_msg}")
            return {
                'success': False,
                'error': error_msg
            }

    def _share_file(self, file_id: str, email_addresses: List[str]):
        """Share a file with specific email addresses"""
        try:
            for email in email_addresses:
                email = email.strip()
                if not email:
                    continue

                permission = {
                    'type': 'user',
                    'role': 'reader',  # Can be 'reader', 'writer', or 'commenter'
                    'emailAddress': email
                }

                self.service.permissions().create(
                    fileId=file_id,
                    body=permission,
                    sendNotificationEmail=True
                ).execute()

                logger.info(f"   Shared with: {email}")

        except Exception as e:
            logger.warning(f"⚠️  Failed to share file with {email_addresses}: {e}")

    def _get_mime_type(self, file_path: str) -> str:
        """Determine MIME type from file extension"""
        extension = os.path.splitext(file_path)[1].lower()

        mime_types = {
            '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            '.xls': 'application/vnd.ms-excel',
            '.csv': 'text/csv',
            '.pdf': 'application/pdf',
            '.json': 'application/json',
            '.txt': 'text/plain',
            '.zip': 'application/zip',
        }

        return mime_types.get(extension, 'application/octet-stream')

    def create_folder(self, folder_name: str, parent_folder_id: Optional[str] = None) -> Dict:
        """
        Create a folder in Google Drive

        Args:
            folder_name: Name of folder to create
            parent_folder_id: Parent folder ID (None = root)

        Returns:
            Dict with folder info or error
        """
        try:
            file_metadata = {
                'name': folder_name,
                'mimeType': 'application/vnd.google-apps.folder'
            }

            if parent_folder_id:
                file_metadata['parents'] = [parent_folder_id]

            folder = self.service.files().create(
                body=file_metadata,
                fields='id, name, webViewLink'
            ).execute()

            logger.info(f"✅ Folder created: {folder.get('name')} (ID: {folder.get('id')})")

            return {
                'success': True,
                'folder_id': folder.get('id'),
                'folder_name': folder.get('name'),
                'web_view_link': folder.get('webViewLink')
            }

        except Exception as e:
            error_msg = f"Failed to create folder: {str(e)}"
            logger.error(f"❌ {error_msg}")
            return {
                'success': False,
                'error': error_msg
            }

    def list_files(self, folder_id: Optional[str] = None, max_results: int = 10) -> List[Dict]:
        """List files in Google Drive"""
        try:
            query = f"'{folder_id}' in parents" if folder_id else None

            results = self.service.files().list(
                q=query,
                pageSize=max_results,
                fields="files(id, name, mimeType, modifiedTime, size, webViewLink)"
            ).execute()

            files = results.get('files', [])
            return files

        except Exception as e:
            logger.error(f"Failed to list files: {e}")
            return []
