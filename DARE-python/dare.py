#!/usr/bin/env python
import sys
from os.path import join, expanduser
import sqlite3
import time
import re
import rest
import argparse

#hack to set the encoding when the stdout is piped
if not sys.stdout.encoding:
    reload(sys)
    sys.setdefaultencoding('utf-8')


class Store(object):

    def __init__(self):
        do_modifications(create_schema)

    @property
    def robots(self):
        robots_data = as_robot_maps(do_query(query_robots))
        for robot in robots_data:
            robot_code = robot['code']
            robot['executions'] = self.executions_for(robot_code)
            robot['periodicals'] = self.periodicals_for(robot_code)
        return robots_data

    def executions_for(self, robot_code):
        query_result = do_query(lambda c: c.execute('SELECT * FROM executions WHERE robot_code=? ORDER BY creation_date_s DESC',
                                                    (robot_code,)))
        return as_executions_map(query_result)

    def periodicals_for(self, robot_code):
        query_result = do_query(lambda c: c.execute('SELECT * FROM periodicals WHERE robot_code=? ORDER BY creation_date_s DESC',
                                                    (robot_code,)))
        return as_periodicals_map(query_result)

    def add_robot(self, url, definition):
        code = extract_code_from(url)
        creation_date_seconds = int(time.time())
        statement = 'insert into robots values(?, ?, ?, ?)'
        do_modifications(lambda c: c.execute(statement, (code, url,
                                                         creation_date_seconds,
                                                         definition[:100])))
        return code

    def find_url_robot_for_code(self, code):
        result = do_query('select url from robots where code=?', (code,))
        return self.single_result_or_none(result)

    def find_url_for_execution_with_code(self, code):
        result = do_query('select url from executions where code=?', (code,))
        return self.single_result_or_none(result)

    def find_url_for_periodical_with_code(self, code):
        result = do_query('select url from periodicals where code=?', (code,))
        return self.single_result_or_none(result)

    def single_result_or_none(self, result):
        return len(result) > 0 and result[0][0] or None

    def insert_execution_result_for_robot(self, robot_code, execution_url, inputs):
        code = extract_code_from(execution_url)
        creation_date_seconds = int(time.time())
        query = 'insert into executions values (?, ?, ?, ?, ?)'
        do_modifications(lambda c: c.execute(query, (code, robot_code,
                                                     execution_url, '%s'%inputs,
                                                     creation_date_seconds)))
        return code

    def delete_robot(self, robot_code):
        do_modifications(lambda c: c.execute('DELETE FROM executions WHERE robot_code = ?',
                                             (robot_code,)))
        do_modifications(lambda c: c.execute('DELETE FROM periodicals WHERE robot_code = ?',
                                             (robot_code,)))
        do_modifications(lambda c: c.execute('DELETE FROM robots WHERE code = ?',
                                             (robot_code,)))


    def delete_execution(self, execution_code):
        do_modifications(lambda c: c.execute('DELETE FROM executions WHERE code = ?',
                                             (execution_code,)))

    def delete_periodical(self, periodical_code):
        do_modifications(lambda c: c.execute('DELETE FROM periodicals WHERE code = ?',
                                             (periodical_code,)))

    def insert_periodical_for_robot(self, robot_code, execution_url, period, inputs):
        code = extract_code_from(execution_url)
        creation_date_seconds = int(time.time())
        query = 'INSERT INTO periodicals VALUES (?,?,?,?,?,?)'
        do_modifications(lambda c: c.execute(query, (code, robot_code, execution_url,
                                                     period, '%s'%inputs,
                                                     creation_date_seconds)))
        return code

def do_modifications(action):
    try:
        conn = sqlite3.connect(join(home_dir(), '.dare'))
        c = conn.cursor()
        action(c)
        conn.commit()
    finally:
        c.close()
        conn.close()

def do_query(query, args = None):
    try:
        conn = sqlite3.connect(join(home_dir(), '.dare'))
        c = conn.cursor()
        if isinstance(query, basestring):
            c.execute(query, args)
        else:
            query(c)
        return [row for row in c]
    finally:
        c.close()
        conn.close()

def home_dir():
    return expanduser("~")

def query_robots(c):
    c.execute('SELECT * FROM robots ORDER BY creation_date_s DESC')

def as_robot_maps(list_tuples_from_db):
    return [{'code': t[0],
             'url': t[1],
             'creation_date': as_local_date(t[2]),
             'summary': t[3]} for t in list_tuples_from_db]

def as_executions_map(list_tuples_from_db):
    return [{'code': t[0],
             'url': t[2],
             'inputs': t[3],
             'creation_date': as_local_date(t[4])} for t in list_tuples_from_db]

def as_periodicals_map(list_tuples_from_db):
    return [{'code': t[0],
             'url': t[2],
             'period': t[3],
             'inputs': t[4],
             'creation_date': as_local_date(t[5])} for t in list_tuples_from_db]

def create_schema(cursor):
    cursor.execute('''CREATE TABLE IF NOT EXISTS robots
                 (code TEXT, url TEXT, creation_date_s INTEGER,
                 summary TEXT)''')
    cursor.execute('''CREATE TABLE IF NOT EXISTS executions
                 (code TEXT, robot_code TEXT, url TEXT, inputs TEXT, creation_date_s INTEGER)''')
    cursor.execute('''CREATE TABLE IF NOT EXISTS periodicals
                 (code TEXT, robot_code TEXT, url TEXT, period TEXT, inputs TEXT, creation_date_s INTEGER)''')

def as_local_date(seconds_as_stored_in_db):
    return time.strftime('%a, %d %b %Y %H:%M:%S',
                         time.localtime(int(seconds_as_stored_in_db)))

def as_local_date_from_ms(millis_since_epoch):
    return as_local_date(millis_since_epoch / 1000)

def extract_code_from(url):
    match = re.search(DARE_CODE_PATTERN, url)
    if match:
        return match.group(1)
    else:
        raise Exception("a DARE code has not been found in the url %s" % url)

def hexadecimal(n):
    return '[a-fA-F0-9]{%d}' % n

DARE_CODE_PATTERN = '/(%s-%s-%s-%s-%s)'% (hexadecimal(8), hexadecimal(4),
                                          hexadecimal(4), hexadecimal(4),
                                          hexadecimal(12))

def add_inputs_argument(parser):
    parser.add_argument('inputs', nargs='*', metavar='input',
                        help='The inputs the robot will be executed with')

def create_robot_spec(parser):
    parser.set_defaults(command=create_robot)
    parser.add_argument('-s', '--server', required=True,
                        help='The url of the server on which the robot will be created')
    parser.add_argument('-xml', '--xml', action='store_const', const=True,
                               default=False,
                               help='''If present interpret the input as a xml.
                                       Otherwise in minilanguage.''')
    parser.add_argument('-f', '--file', type=argparse.FileType('r'), default=None,
                               help='If not present use standard input.')

def create_robot(args):
    def send_creation(url, is_xml, input_str):
        dare = rest.DARE(url)
        if is_xml:
            return dare.create_robot_from_xml(input_str)
        else:
            return dare.create_robot(input_str)
    input_str = args.file and args.file.read() or sys.stdin.read()
    input_str = input_str.strip()
    robot = send_creation(args.server, args.xml, input_str)
    store = Store()
    code = store.add_robot(robot.url, input_str)
    print "Robot with code %s created" % code

def execute_robot_spec(parser):
    parser.set_defaults(command=execute_robot)
    add_id_argument(parser, 'robot')
    add_inputs_argument(parser)

def execute_robot(args):
    store = Store()
    robot_code = args.robot
    url_robot = store.find_url_robot_for_code(robot_code)
    if url_robot:
        robot = rest.Robot(url_robot)
        execution = robot.execute(args.inputs)
        execution_code = store.insert_execution_result_for_robot(robot_code,
                                                                 execution.url,
                                                                 args.inputs)
        print "Execution with code %s created" % execution_code
    else:
        print not_found('robot', robot_code)

def not_found(entity_name, code):
    print "Not Found %s with code %s" % (entity_name, code)

PERIOD_HELP = '''The period at which the periodical execution will be executed.
It's composed by an amount followed by a supported unit. Supported units are for days: d, day, days; for hours: h, hour, hours; and for minutes: m, minute, minutes, for minutes. Examples:

Execute each two days: 2days, 2d, 2day, 48h and so on.
Execute each hour and a half: 90m, 90minute, 90minutes.
'''

def create_periodical_spec(parser):
    parser.set_defaults(command=create_periodical)
    add_id_argument(parser, 'robot')
    parser.add_argument('period', help=PERIOD_HELP)
    add_inputs_argument(parser)

def create_periodical(args):
    store = Store()
    robot_code = args.robot
    period = args.period
    robot_url = store.find_url_robot_for_code(robot_code)
    if robot_url:
        robot = rest.Robot(robot_url)
        periodical = robot.periodical(period, args.inputs)
        periodical_code = store.insert_periodical_for_robot(robot_code,
                                                            periodical.url, period,
                                                            args.inputs)
        print "Periodical execution with code %s created" % periodical_code
    else:
        not_found('robot', robot_code)

def show_robot(args):
    store = Store()
    url = store.find_url_robot_for_code(args.robot)
    if url:
        robot = rest.Robot(url)
        result = robot.show()
        show_all = not args.minilanguage and not args.xml
        if show_all:
            print 'robot', result['code']
            print 'Date:', as_local_date_from_ms(result['creationDateMillis'])
        if show_all: print 'XML:'
        if show_all or args.xml: print result['robotXML']

        if show_all: print 'Minilanguage:'
        if show_all or args.minilanguage: print result['robotInMinilanguage']
    else:
        not_found('robot', args.robot)

def add_robot_spec(parser):
    subparsers = parser.add_subparsers(title = 'Command',
                                       description = 'the command to execute. command plus -h for more information')

    list_parser = subparsers.add_parser('list',
                                        help='List robots created by the user')
    list_parser.set_defaults(command=list_robots)

    create_parser = subparsers.add_parser('create',
                                          help='Create a new robot from file or standard input')
    create_robot_spec(create_parser)

    show_parser = subparsers.add_parser('show', help='Show information about robot')
    show_parser.set_defaults(command=show_robot)
    add_id_argument(show_parser, 'robot')
    show_parser.add_argument('-xml', '--xml', action='store_true',
                             help='Show only the xml of the robot')
    show_parser.add_argument('-mini', '--minilanguage',  action='store_true',
                             help='Show only the minilanguage of the robot')

    delete_parser = subparsers.add_parser('delete', help='Delete the robot')
    delete_parser.set_defaults(command=delete_robot)
    add_id_argument(delete_parser, 'robot')

    execute_parser = subparsers.add_parser('execute', help='Execute the robot')
    execute_robot_spec(execute_parser)

    create_periodical_parser = subparsers.add_parser('create-periodical', help='Execute a robot periodically')
    create_periodical_spec(create_periodical_parser)

def list_robots(args):
    store =  Store()
    robots = store.robots
    margin = ' ' * 4
    for r in robots:
        print 'robot', r['code']
        print 'Date: ', r['creation_date']
        print r['summary']
        if r['executions']:
            print 'Executions:'
            for e in r['executions']:
                print margin, 'execution', e['code']
                print margin, 'Date:',  e['creation_date']
                print margin, 'Inputs: ', e['inputs']
        if r['periodicals']:
            print 'Periodicals: '
            for p in r['periodicals']:
                print margin, 'periodical', p['code']
                print margin, 'Date:',  p['creation_date']
                print margin, 'Inputs:', p['inputs']
                print margin, 'Period:', p['period']
        print '-' * 70

def add_show_and_delete_options(parser, on, caption = None):
    caption = caption or on
    parser.add_argument('action', choices=['show', 'delete'],
                        help='The action to do on the %s' % caption)
    add_id_argument(parser, on, caption)

def delete_robot(args):
    store = Store()
    url = store.find_url_robot_for_code(args.robot)
    if url:
        robot = rest.Robot(url)
        robot.delete()
        store.delete_robot(args.robot)
        print "Robot with code %s deleted along its executions" % args.robot
    else:
        not_found('robot', args.robot)

def add_id_argument(parser, on, caption = None):
    parser.add_argument(on, help='The id of the %s' % (caption or on))

def execution_command(args):
    {'show': show_execution,
     'delete': delete_execution}[args.action](args.execution)

def show_execution(execution_id):
    store = Store()
    url = store.find_url_for_execution_with_code(execution_id)
    if url:
        execution = rest.Execution(url)
        result = execution.show()
        print 'execution', execution_id
        print 'Date:', as_local_date_from_ms(result['date'])
        print 'Execution time:', result['executionTime'], 'ms'
        print 'from robot', extract_code_from(result['createdFrom'])
        for l in result['resultLines']:
            print l
    else:
        not_found('execution', execution_id)

def delete_execution(execution_id):
    store = Store()
    url = store.find_url_for_execution_with_code(execution_id)
    if url:
        execution = rest.Execution(url)
        execution.delete()
        store.delete_execution(execution_id)
        print "deleted execution: %s" % execution_id
    else:
        not_found('execution', execution_id)

def periodical_command(args):
    {'show': show_periodical,
     'delete': delete_periodical}[args.action](args.periodical_execution)

def delete_periodical(periodical_id):
    store = Store()
    url = store.find_url_for_periodical_with_code(periodical_id)
    if url:
        periodical = rest.Periodical(url)
        periodical.delete()
        store.delete_periodical(periodical_id)
        print "periodical execution with code %s deleted" % periodical_id
    else:
        not_found('periodical execution', periodical_id)

def show_periodical(periodical_id):
    store = Store()
    url = store.find_url_for_periodical_with_code(periodical_id)
    if url:
        periodical = rest.Periodical(url)
        data = periodical.show()
        #TODO make human readable
        print data
    else:
        not_found('periodical execution', periodical_id)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Connect to DARE')

    subparsers = parser.add_subparsers(title='Entities',
                                       description='Type of entity on which to operate on')

    robot_parser = subparsers.add_parser('robot', help='-h for more help')
    add_robot_spec(robot_parser)

    execution_parser = subparsers.add_parser('execution', help='-h for more help')
    execution_parser.set_defaults(command=execution_command)
    add_show_and_delete_options(execution_parser, 'execution')

    periodical_parser = subparsers.add_parser('periodical', help='-h for more help')
    periodical_parser.set_defaults(command=periodical_command)
    add_show_and_delete_options(periodical_parser, 'periodical_execution',
                                caption='periodical execution')

    parsed_args = parser.parse_args()
    parsed_args.command(parsed_args)
