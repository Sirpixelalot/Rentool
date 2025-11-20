"""
Decompile Wrapper for Android
Provides simple function to decompile .rpyc files to .rpy scripts
"""

import os
import sys
import json
import time
from pathlib import Path

# Add unrpyc to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'unrpyc'))

from unrpyc import decompile_rpyc, Context


def _write_progress(progress_file, data):
    """Write progress data as JSON"""
    if not progress_file:
        return
    try:
        with open(progress_file, 'w') as f:
            json.dump(data, f)
    except:
        pass  # Fail silently - don't crash if progress file write fails


def decompile_directory(source_dir, progress_file=None):
    """
    Decompile all .rpyc files in a directory recursively

    Args:
        source_dir: Directory containing .rpyc files to decompile
        progress_file: Optional path to write progress JSON updates

    Returns:
        dict with 'success' (bool), 'message' (str), 'stats' (dict)
    """
    start_time = time.time()

    try:
        # Validate source_dir
        if source_dir is None or source_dir == '':
            error_msg = str('Source directory is None or empty')
            if progress_file:
                _write_progress(progress_file, {
                    'operation': 'decompile',
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
                stats=dict(total=0, success=0, skipped=0, failed=0)
            )

        if not os.path.exists(source_dir):
            error_msg = str('Source directory does not exist: ' + str(source_dir))
            if progress_file:
                _write_progress(progress_file, {
                    'operation': 'decompile',
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
                stats=dict(total=0, success=0, skipped=0, failed=0)
            )

        # First, recursively find all .rpyc files (case-insensitive)
        rpyc_files = []
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                file_lower = file.lower()
                if file_lower.endswith('.rpyc') or file_lower.endswith('.rpymc'):
                    rpyc_files.append(Path(os.path.join(root, file)))

        total_files = len(rpyc_files)

        if total_files == 0:
            error_msg = str('No .rpyc files found in directory')
            if progress_file:
                _write_progress(progress_file, {
                    'operation': 'decompile',
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
                stats=dict(total=0, success=0, skipped=0, failed=0)
            )

        # Initialize progress
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'decompile',
                'totalFiles': total_files,
                'processedFiles': 0,
                'currentFile': 'Scanning files...',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'in_progress',
                'errorMessage': ''
            })

        # Decompile each file
        stats = {'success': 0, 'skipped': 0, 'failed': 0}

        for idx, rpyc_file in enumerate(rpyc_files):
            context = Context()

            try:
                # Update progress before processing
                if progress_file and (idx % 5 == 0 or idx == total_files - 1):
                    _write_progress(progress_file, {
                        'operation': 'decompile',
                        'totalFiles': total_files,
                        'processedFiles': idx,
                        'currentFile': str(rpyc_file.name),
                        'startTime': int(start_time * 1000),
                        'lastUpdateTime': int(time.time() * 1000),
                        'status': 'in_progress',
                        'errorMessage': ''
                    })

                # Decompile the file (overwrite=False means skip if .rpy exists)
                decompile_rpyc(
                    rpyc_file,
                    context,
                    overwrite=False,      # Skip if .rpy already exists
                    try_harder=False,     # Can enable for obfuscated files
                    dump=False,           # Decompile, don't dump AST
                    comparable=False,
                    no_pyexpr=False,
                    translator=None,
                    init_offset=True,
                    sl_custom_names=None
                )

                # Check result
                if context.state == 'ok':
                    stats['success'] += 1
                elif context.state == 'skip':
                    stats['skipped'] += 1
                else:
                    stats['failed'] += 1

            except Exception as e:
                stats['failed'] += 1
                # Continue with next file even if one fails

        # Mark decompilation as completed
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'decompile',
                'totalFiles': total_files,
                'processedFiles': total_files,
                'currentFile': 'Complete',
                'startTime': int(start_time * 1000),
                'lastUpdateTime': int(time.time() * 1000),
                'status': 'completed',
                'errorMessage': ''
            })

        stats['total'] = total_files

        message = str('Decompiled {} files ({} successful, {} skipped, {} failed)'.format(
            total_files,
            stats['success'],
            stats['skipped'],
            stats['failed']
        ))

        return dict(
            success=True,
            message=message,
            stats=stats
        )

    except Exception as e:
        error_msg = str('Error: {}'.format(str(e)))
        if progress_file:
            _write_progress(progress_file, {
                'operation': 'decompile',
                'totalFiles': total_files if 'total_files' in locals() else 0,
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
            stats=dict(total=0, success=0, skipped=0, failed=0)
        )
