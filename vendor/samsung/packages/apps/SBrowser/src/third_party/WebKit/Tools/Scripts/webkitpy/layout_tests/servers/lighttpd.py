# Copyright (C) 2011 Google Inc. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
#     * Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above
# copyright notice, this list of conditions and the following disclaimer
# in the documentation and/or other materials provided with the
# distribution.
#     * Neither the name of Google Inc. nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

"""A class to help start/stop the lighttpd server used by layout tests."""

import logging
import os
import time

from webkitpy.layout_tests.servers import server_base


_log = logging.getLogger(__name__)


class Lighttpd(server_base.ServerBase):

    def __init__(self, port_obj, output_dir, background=False, port=None,
                 root=None, run_background=None, additional_dirs=None,
                 layout_tests_dir=None, number_of_servers=None):
        """Args:
          output_dir: the absolute path to the layout test result directory
        """
        # Webkit tests
        super(Lighttpd, self).__init__(port_obj, number_of_servers)
        self._name = 'lighttpd'
        self._log_prefixes = ('access.log-', 'error.log-')
        self._output_dir = output_dir
        self._port = port
        self._root = root
        self._run_background = run_background
        self._additional_dirs = additional_dirs
        self._layout_tests_dir = layout_tests_dir

        self._pid_file = self._filesystem.join(self._runtime_path, '%s.pid' % self._name)

        if self._port:
            self._port = int(self._port)

        if not self._layout_tests_dir:
            self._layout_tests_dir = self._port_obj.layout_tests_dir()

        self._webkit_tests = os.path.join(self._layout_tests_dir, 'http', 'tests')
        self._js_test_resource = os.path.join(self._layout_tests_dir, 'resources')
        self._media_resource = os.path.join(self._layout_tests_dir, 'media')

        # Self generated certificate for SSL server (for client cert get
        # <base-path>\chrome\test\data\ssl\certs\root_ca_cert.crt)
        self._pem_file = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), 'httpd2.pem')

        # One mapping where we can get to everything
        self.VIRTUALCONFIG = []

        if self._webkit_tests:
            self.VIRTUALCONFIG.extend(
               # Three mappings (one with SSL) for LayoutTests http tests
               [{'port': 8000, 'docroot': self._webkit_tests},
                {'port': 8080, 'docroot': self._webkit_tests},
                {'port': 8443, 'docroot': self._webkit_tests,
                 'sslcert': self._pem_file}])

    def _prepare_config(self):
        base_conf_file = self._port_obj.path_from_webkit_base('Tools',
            'Scripts', 'webkitpy', 'layout_tests', 'servers', 'lighttpd.conf')
        out_conf_file = os.path.join(self._output_dir, 'lighttpd.conf')
        time_str = time.strftime("%d%b%Y-%H%M%S")
        access_file_name = "access.log-" + time_str + ".txt"
        access_log = os.path.join(self._output_dir, access_file_name)
        log_file_name = "error.log-" + time_str + ".txt"
        error_log = os.path.join(self._output_dir, log_file_name)

        # Write out the config
        base_conf = self._filesystem.read_text_file(base_conf_file)

        # FIXME: This should be re-worked so that this block can
        # use with open() instead of a manual file.close() call.
        f = self._filesystem.open_text_file_for_writing(out_conf_file)
        f.write(base_conf)

        # Write out our cgi handlers.  Run perl through env so that it
        # processes the #! line and runs perl with the proper command
        # line arguments. Emulate apache's mod_asis with a cat cgi handler.
        f.write(('cgi.assign = ( ".cgi"  => "/usr/bin/env",\n'
                 '               ".pl"   => "/usr/bin/env",\n'
                 '               ".asis" => "/bin/cat",\n'
                 '               ".php"  => "%s" )\n\n') %
                                     self._port_obj.path_to_lighttpd_php())

        # Setup log files
        f.write(('server.errorlog = "%s"\n'
                 'accesslog.filename = "%s"\n\n') % (error_log, access_log))

        # Setup upload folders. Upload folder is to hold temporary upload files
        # and also POST data. This is used to support XHR layout tests that
        # does POST.
        f.write(('server.upload-dirs = ( "%s" )\n\n') % (self._output_dir))

        # Setup a link to where the js test templates are stored
        f.write(('alias.url = ( "/js-test-resources" => "%s" )\n\n') %
                    (self._js_test_resource))

        if self._additional_dirs:
            for alias, path in self._additional_dirs.iteritems():
                f.write(('alias.url += ( "%s" => "%s" )\n\n') % (alias, path))

        # Setup a link to where the media resources are stored.
        f.write(('alias.url += ( "/media-resources" => "%s" )\n\n') %
                    (self._media_resource))

        # dump out of virtual host config at the bottom.
        if self._root:
            if self._port:
                # Have both port and root dir.
                mappings = [{'port': self._port, 'docroot': self._root}]
            else:
                # Have only a root dir - set the ports as for LayoutTests.
                # This is used in ui_tests to run http tests against a browser.

                # default set of ports as for LayoutTests but with a
                # specified root.
                mappings = [{'port': 8000, 'docroot': self._root},
                            {'port': 8080, 'docroot': self._root},
                            {'port': 8443, 'docroot': self._root,
                             'sslcert': self._pem_file}]
        else:
            mappings = self.VIRTUALCONFIG
        for mapping in mappings:
            ssl_setup = ''
            if 'sslcert' in mapping:
                ssl_setup = ('  ssl.engine = "enable"\n'
                             '  ssl.pemfile = "%s"\n' % mapping['sslcert'])

            f.write(('$SERVER["socket"] == "127.0.0.1:%d" {\n'
                     '  server.document-root = "%s"\n' +
                     ssl_setup +
                     '}\n\n') % (mapping['port'], mapping['docroot']))
        f.close()

        executable = self._port_obj.path_to_lighttpd()
        module_path = self._port_obj.path_to_lighttpd_modules()
        start_cmd = [executable,
                     # Newly written config file
                     '-f', os.path.join(self._output_dir, 'lighttpd.conf'),
                     # Where it can find its module dynamic libraries
                     '-m', module_path]

        if not self._run_background:
            start_cmd.append(# Don't background
                             '-D')

        # Copy liblightcomp.dylib to /tmp/lighttpd/lib to work around the
        # bug that mod_alias.so loads it from the hard coded path.
        if self._port_obj.host.platform.is_mac():
            tmp_module_path = '/tmp/lighttpd/lib'
            if not self._filesystem.exists(tmp_module_path):
                self._filesystem.maybe_make_directory(tmp_module_path)
            lib_file = 'liblightcomp.dylib'
            self._filesystem.copyfile(self._filesystem.join(module_path, lib_file),
                                      self._filesystem.join(tmp_module_path, lib_file))

        self._start_cmd = start_cmd
        self._env = self._port_obj.setup_environ_for_server('lighttpd')
        self._mappings = mappings