"""
RPA Wrapper for Android
Provides simple functions to extract and create RPA archives
"""

import os
import sys
import json
import time
from rpatool import RenPyArchive


def _write_progress(progress_file, data):
    """Write progress data as JSON"""
    if not progress_file:
        return
    try:
        with open(progress_file, 'w') as f:
            json.dump(data, f)
    except:
        pass  # Fail silently - don't crash if progress file write fails


def extract_rpa(rpa_file_path, output_dir, progress_file=None):
    """
    Extract all files from an RPA archive

    Args:
        rpa_file_path: Path to the .rpa file
        output_dir: Directory to extract files to
        progress_file: Optional path to write progress JSON updates

    Returns:
        dict with 'success' (bool), 'message' (str), and 'files' (list)
    """
    start_time = time.time()

    try:
        # Load the archive
        archive = RenPyArchive(rpa_file_path, verbose=False)

        # Get list of files
        files = archive.list()
        total_files = len(files)

        # Initialize progress
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'extract',
                'totalFiles': total_files,
                'processedFiles': 0,
                'currentFile': 'Loading archive...',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'in_progress',
                'errorMessage': ''
            })

        # Create output directory if it doesn't exist
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        # Extract each file
        extracted_files = []
        for idx, filename in enumerate(files):
            try:
                contents = archive.read(filename)

                # Create subdirectories if needed
                output_path = os.path.join(output_dir, filename)
                output_file_dir = os.path.dirname(output_path)
                if output_file_dir and not os.path.exists(output_file_dir):
                    os.makedirs(output_file_dir)

                # Write file
                with open(output_path, 'wb') as f:
                    f.write(contents)

                extracted_files.append(str(filename))

                # Update progress every 5 files (to reduce I/O)
                if progress_file and (idx % 5 == 0 or idx == total_files - 1):
                    _write_progress(progress_file, {
                        'operation': 'extract',
                        'totalFiles': total_files,
                        'processedFiles': idx + 1,
                        'currentFile': str(filename),
                        'startTime': int(start_time * 1000),
                        'lastUpdateTime': int(time.time() * 1000),
                        'status': 'in_progress',
                        'errorMessage': ''
                    })

            except Exception as e:
                # Error occurred - stop immediately and report
                error_msg = str('Error extracting {}: {}'.format(filename, str(e)))
                if progress_file:
                    _write_progress(progress_file, {
                        'operation': 'extract',
                        'totalFiles': total_files,
                        'processedFiles': idx,
                        'currentFile': str(filename),
                        'startTime': int(start_time * 1000),
                        'lastUpdateTime': int(time.time() * 1000),
                        'status': 'failed',
                        'errorMessage': error_msg
                    })
                return dict(
                    success=False,
                    message=error_msg,
                    files=list(extracted_files)
                )

        # Mark extraction as completed
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'extract',
                'totalFiles': total_files,
                'processedFiles': total_files,
                'currentFile': 'Complete',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'completed',
                'errorMessage': ''
            })

        return dict(
            success=True,
            message=str('Successfully extracted {} files'.format(len(extracted_files))),
            files=list(extracted_files)
        )

    except Exception as e:
        error_msg = str('Error: {}'.format(str(e)))
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'extract',
                'totalFiles': 0,
                'processedFiles': 0,
                'currentFile': '',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'failed',
                'errorMessage': error_msg
            })
        return dict(
            success=False,
            message=error_msg,
            files=list()
        )


def create_rpa(source_dir, output_rpa_path, version=3, key=0xDEADBEEF, progress_file=None):
    """
    Create an RPA archive from a directory

    Args:
        source_dir: Directory containing files to archive
        output_rpa_path: Path for the output .rpa file
        version: RPA version (2 or 3, default 3)
        key: Obfuscation key for RPA v3 (default 0xDEADBEEF)
        progress_file: Optional path to write progress JSON updates

    Returns:
        dict with 'success' (bool), 'message' (str), and 'files' (list)
    """
    start_time = time.time()

    try:
        # First, count total files to process
        total_files = 0
        for root, dirs, files in os.walk(source_dir):
            total_files += len(files)

        if total_files == 0:
            error_msg = str('No files found in source directory')
            if progress_file:
                _write_progress(progress_file, {
                    'operation': 'create',
                    'totalFiles': 0,
                    'processedFiles': 0,
                    'currentFile': '',
                    'startTime': int(start_time * 1000),
                    'lastUpdateTime': int(time.time() * 1000),
                    'status': 'failed',
                    'errorMessage': error_msg
                })
            return dict(
                success=False,
                message=error_msg,
                files=list()
            )

        # Initialize progress
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'create',
                'totalFiles': total_files,
                'processedFiles': 0,
                'currentFile': 'Initializing archive...',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'in_progress',
                'errorMessage': ''
            })

        # Create new archive
        archive = RenPyArchive(version=version, key=key, verbose=False)

        # Recursively add all files from source directory
        added_files = []
        file_index = [0]  # Using list to allow modification in nested function

        def add_directory(dir_path, archive_prefix=''):
            for item in os.listdir(dir_path):
                item_path = os.path.join(dir_path, item)
                archive_path = os.path.join(archive_prefix, item).replace(os.sep, '/')

                if os.path.isdir(item_path):
                    # Recursively add subdirectory
                    add_directory(item_path, archive_path)
                else:
                    # Add file
                    try:
                        with open(item_path, 'rb') as f:
                            contents = f.read()
                        archive.add(archive_path, contents)
                        added_files.append(str(archive_path))

                        # Update progress every 5 files (to reduce I/O)
                        file_index[0] += 1
                        if progress_file and (file_index[0] % 5 == 0 or file_index[0] == total_files):
                            _write_progress(progress_file, {
                                'operation': 'create',
                                'totalFiles': total_files,
                                'processedFiles': file_index[0],
                                'currentFile': str(archive_path),
                                'startTime': int(start_time * 1000),
                                'lastUpdateTime': int(time.time() * 1000),
                                'status': 'in_progress',
                                'errorMessage': ''
                            })

                    except Exception as e:
                        # Error occurred - stop immediately and report
                        error_msg = str('Error adding {}: {}'.format(item_path, str(e)))
                        if progress_file:
                            _write_progress(progress_file, {
                                'operation': 'create',
                                'totalFiles': total_files,
                                'processedFiles': file_index[0],
                                'currentFile': str(archive_path),
                                'startTime': int(start_time * 1000),
                                'lastUpdateTime': int(time.time() * 1000),
                                'status': 'failed',
                                'errorMessage': error_msg
                            })
                        raise Exception(error_msg)

        # Add all files
        add_directory(source_dir)

        # Save archive
        archive.save(output_rpa_path)

        # Mark creation as completed
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'create',
                'totalFiles': total_files,
                'processedFiles': total_files,
                'currentFile': 'Complete',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'completed',
                'errorMessage': ''
            })

        return dict(
            success=True,
            message=str('Successfully created archive with {} files'.format(len(added_files))),
            files=list(added_files)
        )

    except Exception as e:
        error_msg = str('Error: {}'.format(str(e)))
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'create',
                'totalFiles': total_files if 'total_files' in locals() else 0,
                'processedFiles': file_index[0] if 'file_index' in locals() else 0,
                'currentFile': '',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'failed',
                'errorMessage': error_msg
            })
        return dict(
            success=False,
            message=error_msg,
            files=list()
        )


def list_rpa_files(rpa_file_path):
    """
    List all files in an RPA archive

    Args:
        rpa_file_path: Path to the .rpa file

    Returns:
        dict with 'success' (bool), 'message' (str), 'files' (list), and 'version' (str)
    """
    try:
        archive = RenPyArchive(rpa_file_path, verbose=False)
        files = archive.list()
        files.sort()

        return dict(
            success=True,
            message=str('Found {} files'.format(len(files))),
            files=list([str(f) for f in files]),
            version=str(archive.version)
        )

    except Exception as e:
        return dict(
            success=False,
            message=str('Error: {}'.format(str(e))),
            files=list(),
            version=str('unknown')
        )
