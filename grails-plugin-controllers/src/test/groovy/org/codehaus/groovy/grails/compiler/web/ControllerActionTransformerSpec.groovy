package org.codehaus.groovy.grails.compiler.web

import grails.util.BuildSettings
import grails.util.GrailsWebUtil
import grails.web.Action

import org.codehaus.groovy.grails.commons.spring.GrailsWebApplicationContext
import org.codehaus.groovy.grails.compiler.injection.ClassInjector
import org.codehaus.groovy.grails.compiler.injection.GrailsAwareClassLoader
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

class ControllerActionTransformerSpec extends Specification {

    def gcl

    void setup() {
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'true'
        gcl = new GrailsAwareClassLoader()
        def transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) { true }
        }

        def transformer2 = new ControllerTransformer() {
            @Override
            boolean shouldInject(URL url) { true }
        }

        gcl.classInjectors = [transformer,transformer2] as ClassInjector[]
        def webRequest = GrailsWebUtil.bindMockWebRequest()
        def appCtx = new GrailsWebApplicationContext()
        def servletContext = webRequest.servletContext
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
    }

    void "Test that a closure action has changed to method"() {

        when:
            def cls = gcl.parseClass('''
            class TestTransformedToController {

                def action = {
                }

                }
            ''')
            def controller = cls.newInstance()

        then:
          controller
          controller.getClass().getMethod("action", [] as Class[]) != null
    }

    void 'Test that user applied annotations are applied to generated action methods'() {
        given:
        def cls = gcl.parseClass('''
        class SomeController {
            @Deprecated
            def action1(){}
            @Deprecated
            def action2(String paramName){}
}
''')

        when:
        def action1NoArgMethod = cls.getMethod('action1')

        then:
        action1NoArgMethod.getAnnotation(Action)
        action1NoArgMethod.getAnnotation(Deprecated)

        when:
        def action2MethodWithStringArg = cls.getMethod('action2', [String] as Class[])

        then:
        !action2MethodWithStringArg.getAnnotation(Action)
        action2MethodWithStringArg.getAnnotation(Deprecated)

        when:
        def action2NoArgMethod = cls.getMethod('action2')

        then:
        action2NoArgMethod.getAnnotation(Action)
        action2NoArgMethod.getAnnotation(Deprecated)
    }

    void 'Test action overiding'() {
        given:
            def superControllerClass = gcl.parseClass('''
            class SuperController {
                def methodAction() {
                    [ actionInvoked: 'SuperController.methodAction' ]
                }
                def methodActionWithParam(String s) {
                    [ paramValue: s ]
                }
            }
''')
            def superController = superControllerClass.newInstance()
            def subControllerClass = gcl.parseClass('''
            class SubController extends SuperController {
                def methodAction() {
                    [ actionInvoked: 'SubController.methodAction' ]
                }
                def methodActionWithParam(Integer i) {
                    [ paramValue: i ]
                }
            }
''')
            def subController = subControllerClass.newInstance()

        when:
            def model = superController.methodAction()

        then:
            'SuperController.methodAction' == model.actionInvoked

        when:
            superController.params.s = 'Super Controller Param'
            model = superController.methodActionWithParam()

        then:
            'Super Controller Param' == model.paramValue

        when:
            model = subController.methodAction()

        then:
            'SubController.methodAction' == model.actionInvoked

        when:
            subController.params.s = 'Super Controller Param'
            model = subController.methodActionWithParam()

        then:
            null == model.paramValue

        when:
            subController.params.i = 42
            model = subController.methodActionWithParam()

        then:
            42 == model.paramValue
    }

   /* void "Test annotated controllers"() {
        when:
        def cls = gcl.parseClass('''
            class AnnotatedControllerTransformer1Controller {
                def action = {
                }
            }
            ''')

        def controller = cls.newInstance()

        then:
        controller
        controller.getClass().getMethod("action", [] as Class[]) != null

    }
*/

    def cleanup() {
        RequestContextHolder.setRequestAttributes(null)
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'false'
    }
}
