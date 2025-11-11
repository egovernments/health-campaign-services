"""
Google Drive Upload Utility
Handles authentication and uploading files to Google Drive using Service Account
"""

import os
import json
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from googleapiclient.errors import HttpError


class GoogleDriveUploader:
    """
    Utility class for uploading files to Google Drive using Service Account credentials
    """

    # Google Drive API scopes
    SCOPES = ['https://www.googleapis.com/auth/drive.file']

    def __init__(self, credentials_path):
        """
        Initialize the Google Drive uploader

        Args:
            credentials_path (str): Path to the Service Account JSON credentials file
        """
        self.credentials_path = credentials_path
        self.service = None
        self._authenticate()

    def _authenticate(self):
        """
        Authenticate with Google Drive API using Service Account credentials
        """
        try:
            print(f"🔐 Authenticating with Google Drive using credentials: {self.credentials_path}")

            # Load credentials from JSON file
            credentials = service_account.Credentials.from_service_account_file(
                self.credentials_path,
                scopes=self.SCOPES
            )

            # Build the Drive service
            self.service = build('drive', 'v3', credentials=credentials)
            print("✅ Successfully authenticated with Google Drive API")

        except Exception as e:
            print(f"❌ Authentication failed: {str(e)}")
            raise

    def upload_file(self, file_path, folder_id=None, file_name=None):
        """
        Upload a file to Google Drive

        Args:
            file_path (str): Path to the local file to upload
            folder_id (str, optional): Google Drive folder ID to upload to. If None, uploads to root
            file_name (str, optional): Name for the file in Drive. If None, uses local filename

        Returns:
            dict: File metadata including file ID and webViewLink
        """
        try:
            if not os.path.exists(file_path):
                raise FileNotFoundError(f"File not found: {file_path}")

            # Use local filename if not specified
            if file_name is None:
                file_name = os.path.basename(file_path)

            print(f"📤 Uploading file: {file_name}")
            print(f"   Local path: {file_path}")
            print(f"   File size: {os.path.getsize(file_path)} bytes")

            # Determine MIME type based on file extension
            mime_type = self._get_mime_type(file_path)

            # File metadata
            file_metadata = {
                'name': file_name
            }

            # Add parent folder if specified
            if folder_id:
                file_metadata['parents'] = [folder_id]
                print(f"   Target folder ID: {folder_id}")

            # Upload the file
            media = MediaFileUpload(
                file_path,
                mimetype=mime_type,
                resumable=True
            )

            file = self.service.files().create(
                body=file_metadata,
                media_body=media,
                fields='id, name, webViewLink, size, createdTime'
            ).execute()

            print(f"✅ File uploaded successfully!")
            print(f"   File ID: {file.get('id')}")
            print(f"   File name: {file.get('name')}")
            print(f"   Size: {file.get('size')} bytes")
            print(f"   View link: {file.get('webViewLink')}")

            return file

        except HttpError as error:
            print(f"❌ Google Drive API error: {error}")
            raise
        except Exception as e:
            print(f"❌ Upload failed: {str(e)}")
            raise

    def upload_folder(self, folder_path, parent_folder_id=None):
        """
        Upload all files from a local folder to Google Drive

        Args:
            folder_path (str): Path to the local folder containing files to upload
            parent_folder_id (str, optional): Google Drive folder ID to upload to

        Returns:
            list: List of uploaded file metadata
        """
        try:
            if not os.path.exists(folder_path):
                raise FileNotFoundError(f"Folder not found: {folder_path}")

            if not os.path.isdir(folder_path):
                raise ValueError(f"Path is not a directory: {folder_path}")

            print(f"📁 Uploading files from folder: {folder_path}")

            uploaded_files = []

            # Walk through the folder and upload all files
            for root, dirs, files in os.walk(folder_path):
                for file in files:
                    file_path = os.path.join(root, file)

                    # Skip hidden files
                    if file.startswith('.'):
                        print(f"⏭️  Skipping hidden file: {file}")
                        continue

                    try:
                        result = self.upload_file(
                            file_path=file_path,
                            folder_id=parent_folder_id,
                            file_name=file
                        )
                        uploaded_files.append(result)
                    except Exception as e:
                        print(f"⚠️  Failed to upload {file}: {str(e)}")
                        continue

            print(f"✅ Uploaded {len(uploaded_files)} file(s) from folder")
            return uploaded_files

        except Exception as e:
            print(f"❌ Folder upload failed: {str(e)}")
            raise

    def _get_mime_type(self, file_path):
        """
        Determine MIME type based on file extension

        Args:
            file_path (str): Path to the file

        Returns:
            str: MIME type
        """
        extension = os.path.splitext(file_path)[1].lower()

        mime_types = {
            '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            '.xls': 'application/vnd.ms-excel',
            '.csv': 'text/csv',
            '.pdf': 'application/pdf',
            '.txt': 'text/plain',
            '.json': 'application/json',
            '.zip': 'application/zip',
        }

        return mime_types.get(extension, 'application/octet-stream')

    def list_files_in_folder(self, folder_id):
        """
        List all files in a Google Drive folder

        Args:
            folder_id (str): Google Drive folder ID

        Returns:
            list: List of files in the folder
        """
        try:
            query = f"'{folder_id}' in parents and trashed=false"

            results = self.service.files().list(
                q=query,
                fields='files(id, name, size, createdTime, webViewLink)',
                orderBy='createdTime desc'
            ).execute()

            files = results.get('files', [])

            print(f"📋 Found {len(files)} file(s) in folder {folder_id}")
            for file in files:
                print(f"   - {file.get('name')} (ID: {file.get('id')})")

            return files

        except HttpError as error:
            print(f"❌ Failed to list files: {error}")
            raise


def main():
    """
    Main function to upload reports to Google Drive
    """
    import sys

    # Configuration from environment variables
    credentials_path = os.getenv('GOOGLE_DRIVE_CREDENTIALS_PATH', '/app/credentials/google-drive-credentials.json')
    folder_id = os.getenv('GOOGLE_DRIVE_FOLDER_ID')
    reports_path = os.getenv('REPORTS_PATH', '/app/REPORTS_GENERATION/FINAL_REPORTS')

    print("=" * 60)
    print("Google Drive Upload Utility")
    print("=" * 60)
    print(f"Credentials path: {credentials_path}")
    print(f"Target folder ID: {folder_id}")
    print(f"Reports path: {reports_path}")
    print("=" * 60)

    # Validate inputs
    if not os.path.exists(credentials_path):
        print(f"❌ Credentials file not found: {credentials_path}")
        sys.exit(1)

    if not folder_id:
        print("❌ GOOGLE_DRIVE_FOLDER_ID environment variable not set")
        sys.exit(1)

    if not os.path.exists(reports_path):
        print(f"❌ Reports path not found: {reports_path}")
        sys.exit(1)

    try:
        # Initialize uploader
        uploader = GoogleDriveUploader(credentials_path)

        # Upload all files from the reports folder
        uploaded_files = uploader.upload_folder(
            folder_path=reports_path,
            parent_folder_id=folder_id
        )

        print("\n" + "=" * 60)
        print(f"✅ Upload completed! Total files uploaded: {len(uploaded_files)}")
        print("=" * 60)

        # Print summary
        for file in uploaded_files:
            print(f"✓ {file.get('name')}")
            print(f"  Link: {file.get('webViewLink')}")

        sys.exit(0)

    except Exception as e:
        print(f"\n❌ Upload failed with error: {str(e)}")
        sys.exit(1)


if __name__ == '__main__':
    main()
