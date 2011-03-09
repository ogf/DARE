require 'transformer'

describe "Transformers" do

  it "should require transformer_class" do
    lambda {
      Transformer.new :description=> "bla"
    }.should raise_error(ArgumentError, "transformer_class is required")
  end

  it "shouldn't description be changed" do
    t = Transformer.new :description =>  "blabla",
                        :transformer_class =>'SimpleTransformer'
    lambda{t.description = "blabla"}.should raise_error
  end

  it "should accept description" do
    t = Transformer.new :description=>"blabla",
                        :transformer_class =>'SimpleTransformer'
    t.description.should == "blabla"
  end

  it "should have transformer_class name as default value for description" do
    t = Transformer.new :transformer_class =>'SimpleTransformer'
    t.description.should == "SimpleTransformer"
  end

  it "should accept branchtype parameter" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :branchtype => "BRANCH_DUPLICATED"
    t.branchtype.should == :BRANCH_DUPLICATED
  end

  it "should have branchtype CASCADE as default" do
    t = Transformer.new :transformer_class =>'SimpleTransformer'
    t.branchtype.should == :CASCADE
  end

  it "should accept parameter branchmergemode" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :branchmergemode =>:ORDERED
    t.branchmergemode.should == :ORDERED
  end

  it "should have branchmergemode SCATTERED as default" do
    t = Transformer.new :transformer_class =>'SimpleTransformer'
    t.branchmergemode.should == :SCATTERED
  end

  it "should accept parameter loop" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :loop => true
    t.loop?.should be_true
  end

  it "should keep parameters" do
    t = Transformer.new :transformer_class =>'SimpleTransformer', :other =>"bla"
    t.params[:other].should_not be_nil
  end

  it "should let add children" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :loop => true
    child = Transformer.new :transformer_class =>'SimpleTransformer',
                        :loop => true
    t.add_child child
    t.children.should == [child]
  end

  it "must have an empty list as children initially" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :loop => true
    t.children.should_not be_nil
  end

  it "should not let modify original children collection" do
    t = Transformer.new :transformer_class =>'SimpleTransformer',
                        :loop => true
    t.add_child(Transformer.new(:transformer_class =>'SimpleTransformer',
                        :loop => true))
    t.children.clear
    t.children.length.should == 1
  end


  shared_examples_for "transformer invariants" do
    it "should have a not nil description" do
      @transformer.description.should_not be_nil
    end
    it "should have a not nil transformer_class" do
      @transformer.transformer_class.should_not be_nil
    end
    it "should have a not nil branchtype" do
      @transformer.branchtype.should_not be_nil
    end

    it "should have a not nil branchmergemode" do
      @transformer.branchmergemode.should_not be_nil
    end

    def mock_children(transformer, mock = mock("child serializer"))
      attributes_mock = mock("attributes")
      mock.stub!(:attributes).and_return attributes_mock
      attributes_mock.should_receive("[]=").with('class', @transformer.transformer_class.to_s)
      attributes_mock.should_receive("[]=").
        with('branchtype', @transformer.branchtype.to_s)
      attributes_mock.should_receive("[]=").
        with('branchmergemode', @transformer.branchmergemode.to_s)
      attributes_mock.should_receive("[]=").with('loop', @transformer.loop?.to_s)
      pos = -1
      mock.should_receive("add_element").exactly(transformer.children.length).and_return{
                                           pos = pos + 1
                                           mock_children(transformer.children[pos])
                                          }
      mock.should_receive("add_param").exactly(transformer.params.size)
      mock
    end

    it "should traverse tranformer" do
      mock = mock("top serializer")
      @transformer.export mock_children(@transformer, mock)
    end
  end

  describe "children nested" do
    before(:all) do
      @transformer = PatternMatcher.new :pattern => 'bla'
      child = PatternMatcher.new :pattern => %q{<li><a href="noticia.asp\?idnoticia=.*?">(.*?)</a>},
                                 :dotAll => true
      @transformer.add_child child
      doc = @transformer.export_to_xml
      doc.should_not be_nil
      # doc.write($stdout,4)
    end
    it_should_behave_like "transformer invariants"
  end

  describe PatternMatcher do

    before(:all) do
      @transformer = PatternMatcher.new :pattern => 'bla'
    end

    it_should_behave_like "transformer invariants"

    it "should have a fixed transformer class" do
      p = PatternMatcher.new :pattern =>  'bla'
      p.transformer_class.should == :PatternMatcher
    end

    it "should require parameter pattern" do
      lambda {
        p = PatternMatcher.new({})
      }.should raise_error(ArgumentError, "pattern parameter is required")
    end

    it "should read parameter pattern" do
      p = PatternMatcher.new :pattern =>  'bla'
      p.pattern.should == 'bla'
    end

    it "should let specify pattern as just one param" do
      p = PatternMatcher.new('bla')
      p.pattern.should == 'bla'
    end

    it "should let specify additional params" do
      p = PatternMatcher.new :pattern => 'bla', :bla =>"a"
      p.params[:bla].should == "a"
    end

  end

  describe Language do
    it "should let put transformers in cascade" do
      result = Language.execute { url > patternMatcher(:pattern =>"bla") >
        replacer(:sourceRE =>"e", :dest => "a")}
    end

    def check result
      result.should_not be_nil
      result.transformer_class.should == :SimpleTransformer
      result.branchtype.should be_equal(:CASCADE)
      result.branchmergemode.should be_equal(:SCATTERED)
      result.children.size.should == 3
      first_url = result.children[0]
      pattern_matcher = result.children[1]
      replacer = result.children[2]
      first_url.class.should == URLRetriever
      pattern_matcher.class.should == PatternMatcher
      replacer.class.should == Replacer
    end

    it "should let the language be supplied as a string" do
      result = Language.language_eval %q{url > patternMatcher(:pattern =>"bla") >
        replacer(:sourceRE =>"e", :dest => "a")}
      check result
    end

    it "should let put transformers in branch" do
      result = Language.execute { url > branch(:BRANCH_DUPLICATED,:SCATTERED) {
          patternMatcher(:pattern =>"bla")
          patternMatcher(:pattern =>"eoo") } > appender(:append => "bla")}
      result.should_not be_nil
      result.children.size.should == 3
      branch = result.children[1]
      branch.transformer_class.should == :SimpleTransformer
      branch.branchtype.should == :BRANCH_DUPLICATED
      branch.branchmergemode.should == :SCATTERED
      branch.children.size.should == 2
      branch.children[0].should respond_to(:pattern)
      branch.children[0].pattern.should == "bla"
      branch.children[1].should respond_to(:pattern)
      branch.children[1].pattern.should == "eoo"
      # result.export_to_xml.write($stdout,4)
    end

    it "should not let use | inside a branch" do
      lambda do Language.execute { url > branch(:BRANCH_DUPLICATED,:SCATTERED) {
          patternMatcher(:pattern =>"bla") | patternMatcher(:pattern =>"eoo") }
      }
      end.should raise_error(RuntimeError, "| can't be used in a pipe branch")
    end

    it "should let put transformers in cascade inside branch" do
      result = Language.execute { url > branch(:BRANCH_DUPLICATED,:SCATTERED) {
          patternMatcher(:pattern =>"bla")
          patternMatcher(:pattern =>"eoo")
          pipe{url > patternMatcher(:pattern =>"bla")}
        } > appender(:append => "bla")}
      result.should_not be_nil
      result.children.size.should == 3
      result.children[1].children.size.should == 3
      pipes = result.children[1].children[2]
      pipes.children.size.should == 2
    end

    it "should let do a loop" do
      result = Language.execute { url |
        pipe{ patternMatcher(:pattern =>"bla") | url}.repeat?{
          patternMatcher(:pattern => "prueba")
        } | appender(:append => "bla")
      }
      result.should_not be_nil
      result.children.size.should == 3
      result.children[1].children.size.should == 3
      result.children[1].children[0].transformer_class.should == :PatternMatcher
      result.children[1].children[0].pattern.should == "prueba"
      result.children[2].children.size.should == 0
      result.children[2].transformer_class.should == :Appender
    end

    it "should let put several transformer serially connected in the repeat clause" do
      result = Language.execute { url |
        pipe{ patternMatcher(:pattern => "bla") | url}.repeat?{
          patternMatcher(:pattern => "prueba") > url
        } | appender(:append => "bla")
      }
      result.should_not be_nil
      result.children.size.should == 3
      result.children[1].children.size.should == 3
      result.children[1].children[0].transformer_class.should == :SimpleTransformer
      result.children[1].children[0].children.size.should == 2
    end

    it "should let put several transformers paralelly connected in the repeat clause" do
      result = Language.execute { url |
        pipe{ patternMatcher(:pattern => "bla") | url}.repeat?(:BRANCH_DUPLICATED,
                                                               :SCATTERED){
          patternMatcher(:pattern => "prueba")
          url
        } | appender(:append => "bla")
      }
      result.should_not be_nil
      result.children.size.should == 3
      result.children[1].children.size.should == 3
      repeat_clause = result.children[1].children[0]
      repeat_clause.transformer_class.should == :SimpleTransformer
      repeat_clause.children.size.should == 2
      repeat_clause.branchtype.should == :BRANCH_DUPLICATED
      repeat_clause.branchmergemode.should == :SCATTERED
    end
  end
end