require 'java'
module XML
  include_package 'javax.xml.parsers'
  include_package 'org.w3c.dom'
end
class NodeList
  include Enumerable

  def initialize node_list
    @node_list = node_list
  end

  attr_reader :node_list

  def each
    (0...node_list.getLength()).each do |i|
      yield node_list.item(i)
    end
  end

  def select_with_name name
    select{|n| n.java_kind_of?(XML::Element) && name == n.nodeName}
  end

end

class Language

  def self.add_transformer transformer_klass
    define_method(transformer_klass.name) do |*args|
      params = args[0]
      transformer_added_action(transformer_klass.new(params || Hash.new))
      self
    end
  end

  def self.execute &block
    l = Language.new &block
    l.transformer
  end

  def self.language_eval str, filename=nil, lineno=nil
    l = Language.new
    args = [str,filename,lineno].reject{|x| x.nil?}
    l.instance_eval *args
    l.transformer
  end

  def initialize *params, &block
    hash = {}
    hash[:branchtype] = params[0] if params[0]
    hash[:branchmergemode] = params[1] if params[1]
    @transformer = SimpleTransformer.new(hash)
    instance_eval(&block) if block
  end

  def transformer
    @transformer
  end

  def transformer_added_action transformer
    @transformer.add_child transformer
  end
  protected :transformer_added_action


  def branch *params, &block
    unless params[0] && params[1]
      raise ArgumentError, "branchtype and branchmerge mode required"
    end
    l = Language.new *params, &block
    @transformer.add_child l.transformer
  end

  def pipe &block
    pipe = Language.new &block
    @transformer.add_child pipe.transformer
    pipe
  end

  def > other
    self | other
  end

  def | other
    if @transformer.branchtype != :CASCADE
      raise "| can't be used in a pipe branch"
    end
    self
  end

  def repeat? *params, &block
    clause = RepeatClause.new *params, &block
    if clause.transformer
      @transformer.do_loop_with clause.transformer
    end
  end

end

class RepeatClause < Language
  def initialize *params, &block
    super *params, &block
  end

  alias_method :standard_added_action, :transformer_added_action

  def transformer_added_action transformer
    (@transformers||=[]) << transformer
  end

  def transformer
    (@transformers || []).each do |t|
      standard_added_action t
    end
    @transformers.clear if @transformers
    if super.children.size == 1
      super.children[0]
    else
      super
    end
  end
end

class Param

  attr_accessor :name
  attr_accessor :required
  attr_accessor :default_value

  def initialize(name, params)
    self.name = name.to_sym
    self.required = if params[:required] then true; else false end
    self.default_value = params[:default_value]
  end

end


class AttributesWrapper
  def initialize(element)
    @element = element
  end
  def []=(name,value)
    @element.setAttribute(name.to_s,value)
  end
end

class DocumentWrapper

  def self.create
    factory = XML::DocumentBuilderFactory.newInstance
    factory.setNamespaceAware false
    DocumentWrapper.new(factory.newDocumentBuilder.newDocument)
  end

  def initialize(doc, node=doc)
    @doc = doc
    @node = node
  end

  def add_element name
    element = @doc.createElement(name)
    @node.appendChild(element)
    DocumentWrapper.new(@doc,element)
  end

  def attributes
    @attributes_wrapper ||= AttributesWrapper.new(@node)
  end

  def wrapped
    @doc
  end
  def node
    @node
  end
  def add_param(key, value)
    param_element = self.add_element 'param'
    param_element.attributes['key']= key.to_s
    text = @doc.createTextNode(value.to_s)
    param_element.node.appendChild(text)
    param_element.attributes['value'] = value.to_s
    param_element
  end
end


class Transformer

  @@rewrite = {"URLRetriever" => "url"}

  def self.lowercase_first word
    word[0,1].downcase+word[1,word.length-1]
  end

  def self.inherited subclass
    Language.add_transformer(subclass)
    (@subclasses||=[]) << subclass
  end

  def self.name
    @@rewrite[self.to_s] || self.lowercase_first(self.to_s)
  end

  def self.subclasses_by_name
    return @subclasses_by_name if @subclasses_by_name
    @subclasses_by_name = @subclasses.inject(Hash.new) {|acc, subclass|
      acc[subclass._transformer_class] = subclass
      acc
    }
  end

  def self.get_transformer_class_for name
    subclasses_by_name[name.to_sym]
  end

  @@default_values = {:branchtype => :CASCADE, :branchmergemode => :SCATTERED,
    :loop => false}

  def self.are_equal?(value, dom_value)
    dom_value == value || dom_value == value.to_s
  end

  def self.extract_params_from_element element
    params = {}
    @@default_values.each_pair do |key, default_value|
      attrValue = element.getAttribute(key.to_s)
      unless are_equal?(default_value, attrValue)
        params[key] = attrValue
      end
    end
    NodeList.new(element.getChildNodes).select_with_name("param").each do |param_element|
      key = param_element.getAttribute('key')
      value = param_element.getTextContent
      unless key.to_sym == :description &&
          value == self._transformer_class.to_s ||
          are_equal?(find_param_definition_default_value(key),value)
        params[key.to_sym] = value
      end
    end
    params
  end

  def self.find_param_definition name
    (@params_definitions||=[]).find{|p| p.name.to_sym == name.to_sym}
  end

  def self.find_param_definition_default_value name
    p = find_param_definition(name)
    p.default_value if p
  end

  def self.as_call params
    result = "#{name}("
    result << /{(.*)}/.match(params.inspect)[1]
    result << ")"
  end

  def self.to_language element, &block
    result = ""
    params = extract_params_from_element element
    if NodeList.new(element.getChildNodes).select_with_name("transformer").empty?
      result << as_call(params)
    else
      if [:branchtype, :branchmergemode].all?{ |key|
          ! params[key]
        }
        result = "pipe { "
        if self != SimpleTransformer
          result << as_call(params) << " | "
        end
        result << block.call(element, " | ")
        result << " }"
      else
        branch_parameters = [:branchtype, :branchmergemode].map { |key|
          (params[key] || @@default_values[key]).to_sym.inspect
        }
        result = "branch(#{branch_parameters[0]},#{branch_parameters[1]}) {\n  "
        result << block.call(element,"\n  ")
        result << "\n}"
      end
    end
    result
  end

  def self.transformer_class value
    @transformer_class = value.to_sym
  end

  def self._transformer_class
    @transformer_class
  end

  def self.param(name, hash)
    (@params_definitions||=[]) << Param.new(name, hash)
  end

  def self.extract_transformer_class value_from_params
    @transformer_class || value_from_params
  end

  def self.extract_params(instance_variable_name, params)
    if ! (Hash === params)
      required = @params_definitions.find_all {|x| x.required}
      if required.size == 1
        value, params = params, {}
        params[required[0].name] = value
      end
    end
    result = Hash.new
    (@params_definitions || []).each do |param_definition|
      if ! params[param_definition.name] && param_definition.required &&
          ! param_definition.default_value
        raise ArgumentError, "#{param_definition.name} parameter is required"
      end
      result[param_definition.name] = params[param_definition.name] ||
        param_definition.default_value
      module_eval <<-END
        def #{param_definition.name}
          @#{instance_variable_name}[:#{param_definition.name}]
        end
      END
    end
    params.merge(result)
  end

  def initialize params
    @description = params[:description]
    @transformer_class = self.class.
      extract_transformer_class(params[:transformer_class])
    @branchtype = (params[:branchtype]||@@default_values[:branchtype]).to_sym
    @branchmergemode = (params[:branchmergemode] ||@@default_values[:branchmergemode]).to_sym
    @loop = if params[:loop] then true; else @@default_values[:loop] end;
    raise ArgumentError, "transformer_class is required" unless @transformer_class
    @params = (self.class.extract_params "params", params).freeze
  end

  private
  def description= desc
      @description = desc
  end

  public
  attr_reader :transformer_class
  attr_reader :branchtype
  attr_reader :branchmergemode

  def export exporter
    exporter.attributes['class'] = transformer_class.to_s
    exporter.attributes['branchtype'] = branchtype.to_s
    exporter.attributes['branchmergemode'] = branchmergemode.to_s
    exporter.attributes['loop'] = loop?.to_s
    params.each_pair do |key, value|
      exporter.add_param(key, value)
    end

    children.each do |child|
      child.export(exporter.add_element("transformer"))
    end
    exporter
  end

  def do_loop_with transformer
    (@children ||= []).insert(0,transformer)
    @loop = true
  end

  def export_to_xml
    doc = DocumentWrapper.create
    robot = doc.add_element 'robot'
    robot.attributes['version'] = '1.0'
    self.export(robot.add_element('transformer'))
    doc.wrapped
  end

  def add_child child
    (@children ||= []) << child
  end

  def children
    Array.new(@children || [])
  end

  def params
    @params
  end

  def loop?
    @loop
  end

  def description
    @description || transformer_class
  end

end

class PatternMatcher < Transformer

  transformer_class :PatternMatcher
  param :pattern, :required => true
  param :dotAll, :default_value => false
end
class Xpath < Transformer
  transformer_class :HTMLMatcher
  param :XPath, :required => true
end

class SimpleTransformer < Transformer
  transformer_class :SimpleTransformer
end

class Appender < Transformer
  transformer_class :Appender
  param :append, :required => true
end

class URLRetriever < Transformer
  transformer_class :URLRetriever
end

class Replacer < Transformer
  transformer_class :Replacer
  param :sourceRE, :required => true
  param :dest, :required => true
end

class Decorator < Transformer
  transformer_class :Decorator
  param :head, :default_value => ''
  param :tail, :default_value => ''
end

class Merger < Transformer
  transformer_class :Merger
end

def get_xml str, filename=nil, lineno=nil
  transformer = Language.language_eval(str,filename,lineno)
  transformer.export_to_xml
end

def to_minilanguage xml
  transformer_visitor = lambda do |element, separator|
    result = ""
    NodeList.new(element.getChildNodes).select_with_name("transformer").
       each_with_index do |element, index|
      transformer_class = Transformer.get_transformer_class_for(
                                           element.getAttribute("class"))
      unless transformer_class
        raise "not found class for #{element.getAttribute('class')}"
      end
      result << separator if index > 0
      result << transformer_class.to_language(element, &transformer_visitor)
    end
    result
  end
  transformer_visitor.call(xml.getDocumentElement," | ")
end
