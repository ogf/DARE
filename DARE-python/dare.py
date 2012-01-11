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

    def __init__(self):
        self._http = httplib2.Http(".cache")

    def do_post(self, url, params = {}, headers = None,
                processor = None):
        headers_ = {'Content-Type': 'application/x-www-form-urlencoded'}
        headers_.update(headers or {})
        entity = urlencode(as_tuples(params))
        resp, content = self._http.request(url, "POST", entity, headers_)
        if processor:
            return processor(resp, content)
        else:
            return (resp, content)

    NO_CONTENT = 204

    SUCESSFUL_RANGE = xrange(200, 300)

    def do_post__location(self, url, params, headers = None):
        return self.do_post(url, params, headers, lambda r, c: r['location'])

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

class Resource(object):

    def __init__(self, server, url):
        self._server = server
        self._url = url.rstrip('/')

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
        Resource.__init__(self, Server(), url)

    def create_robot(self, robot_in_minilanguage):
        url =  self.server.do_post__location(self.path('robot/create'),
                                             {'minilanguage': robot_in_minilanguage})
        return Robot(self.server, url)

    def robot(self, robot_url):
        return Robot(self.server, robot_url)

    def execution(self, execution_url):
        return Execution(self.server, execution_url)

    def execute(self, robot_in_minilanguage, inputs_list):
        params = {'robot': robot_in_minilanguage, 'input': inputs_list}
        url = self.server.do_post__location(self.path('robot/execute'), params)
        return Execution(self.server, url)


    def robot(self, url_robot):
        return Robot(self.server, url_robot)

class Robot(Resource):

    def show(self):
        return self.server.do_get_json(self.url)

    def execute(self, inputs_list):
        url = self.server.do_post__location(self.path("execute"),
                                            {'input': inputs_list})
        return Execution(self.server, url)

    def periodical(self, period, inputs_list):
        url = self.server.do_post__location(self.path("periodical"),
                                            {'period': period,
                                             'input': inputs_list})
        return Periodical(self.server, url)

class Execution(Resource):

    def show(self):
        return self.server.do_get_json(self.url)

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
