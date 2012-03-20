import httplib2
import json
from time import sleep
from urllib import urlencode

def as_seq(value):
    if getattr(value, '__iter__', False):
        return value
    else:
        return [value]

def as_tuples(params):
    items = params.items()
    return [(k, each) for k, v in items for each in as_seq(v)]

class Server(object):

    NO_CONTENT = 204

    SUCESSFUL_RANGE = xrange(200, 300)

    def __init__(self):
        self._http = httplib2.Http(".cache")

    def do_post(self, url, body, headers = {}, processor = None):
        resp, content = self._http.request(url, "POST", body, headers)
        if not resp.status in self.SUCESSFUL_RANGE:
            raise Exception('Unexpected error code %s and content:\n %s'
                            % (resp.status, content))
        if processor:
            return processor(resp, content)
        else:
            return (resp, content)

    def do_post_form(self, url, params = {}, processor = None):
        headers = {'Content-Type': 'application/x-www-form-urlencoded'}
        body = urlencode(as_tuples(params))
        return self.do_post(url, body, headers, processor)

    def do_post_xml(self, url, xml_str, processor = None):
        headers = {'Content-Type': 'application/xml'}
        return self.do_post(url, xml_str, headers, processor)

    def do_post_form__location(self, url, params):
        return self.do_post_form(url, params, lambda r, c: r['location'])

    def do_post_xml__location(self, url, xml_str):
        return self.do_post_xml(url, xml_str, lambda r, c: r['location'])

    def do_get(self, url, headers = None, processor = None, poll_seconds = 2):
        do_request = lambda: self._http.request(url, "GET", headers = headers)
        resp, content = do_request()
        while resp.status == self.NO_CONTENT:
            sleep(poll_seconds)
            resp, content = do_request()
        if not resp.status in self.SUCESSFUL_RANGE:
            return None
        if processor:
            return processor(resp, content)
        else:
            return (resp, content)

    def do_get_json(self, url):
        return self.do_get(url, {'Accept': 'application/json'},
                           lambda r, c: json.loads(c))

    def do_delete(self, url, headers = None):
        return self._http.request(url, "DELETE",  headers = None)

class Resource(object):

    def __init__(self, url, server = Server()):
        self._url = url.rstrip('/')
        self._server = server

    @property
    def server(self):
        return self._server

    @property
    def url(self):
        return self._url

    def path(self, path):
        return self.url + '/' + path.lstrip('/')

class DARE(Resource):

    def __init__(self, url):
        Resource.__init__(self, url)

    def create_robot(self, robot_in_minilanguage):
        url =  self.server.do_post_form__location(self.path('robot/create'),
                                             {'minilanguage': robot_in_minilanguage})
        return Robot(url, self.server)

    def create_robot_from_xml(self, robot_xml_str):
        url = self.server.do_post_xml__location(self.path('robot/create'),
                                                robot_xml_str)
        return Robot(url, self.server)

    def robot(self, robot_url):
        return Robot(robot_url, self.server)

    def execution(self, execution_url):
        return Execution(execution_url, self.server)

    def execute(self, robot_in_minilanguage, inputs_list):
        params = {'robot': robot_in_minilanguage, 'input': inputs_list}
        url = self.server.do_post_form__location(self.path('robot/execute'), params)
        return Execution(url, self.server)

    def robot(self, url_robot):
        return Robot(url_robot, self.server)

class Robot(Resource):

    def show(self):
        return self.server.do_get_json(self.url)

    def execute(self, inputs_list):
        url = self.server.do_post_form__location(self.path("execute"),
                                            {'input': inputs_list})
        return Execution(url, self.server)

    def periodical(self, period, inputs_list):
        url = self.server.do_post_form__location(self.path("periodical"),
                                            {'period': period,
                                             'input': inputs_list})
        return Periodical(url, self.server)

    def delete(self):
        return self.server.do_delete(self.url)

class Execution(Resource):

    def show(self):
        return self.server.do_get_json(self.url)

    def delete(self):
        return self.server.do_delete(self.url)

class Period(object):

    def __init__(self,  unit):
        self._unit = unit

    def amount(self, amount):
        return "%d%s"%(amount, self._unit)

Period.HOUR = Period('h')
Period.DAY = Period('d')
Period.MINUTE = Period('m')

class Periodical(Resource):

    def show(self):
        return self.server.do_get_json(self.url)


    def delete(self):
        return self.server.do_delete(self.url)
