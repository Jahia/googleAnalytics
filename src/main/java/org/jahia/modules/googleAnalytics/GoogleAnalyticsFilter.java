/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.googleAnalytics;

import net.htmlparser.jericho.*;
import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.cache.AggregateCacheFilter;
import org.jahia.services.templates.JahiaTemplateManagerService.TemplatePackageRedeployedEvent;
import org.jahia.utils.ScriptEngineUtils;
import org.jahia.utils.WebUtils;
import org.slf4j.*;
import org.slf4j.Logger;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.SimpleScriptContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

/**
 * User: david
 * Date: 2/25/11
 * Time: 11:28 AM
 */
public class GoogleAnalyticsFilter extends AbstractFilter implements ApplicationListener<ApplicationEvent> {

    private static final Long DEFAULT_COOKIE_EXPIRES = 60L * 60L * 24L * 365L;
    private static final String COOKIE_EXPIRES = "cookieExpires";

    private static Logger logger = LoggerFactory.getLogger(GoogleAnalyticsFilter.class);

    private ScriptEngineUtils scriptEngineUtils;
    
    private String template;
    
    private String resolvedTemplate;
    
    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        String out = previousOut;
        JCRSiteNode site = renderContext.getSite();
        String webPropertyID = site.hasProperty("webPropertyID") ? site.getProperty("webPropertyID").getString() : null;
        if (StringUtils.isNotEmpty(webPropertyID)) {
            String script = getResolvedTemplate();
            if (script != null) {
                Source source = new Source(previousOut);
                OutputDocument outputDocument = new OutputDocument(source);
                List<Element> headElementList = source.getAllElements(HTMLElementName.HEAD);
                for (Element element : headElementList) {
                    final EndTag headEndTag = element.getEndTag();
                    String extension = StringUtils.substringAfterLast(template, ".");
                    ScriptEngine scriptEngine = scriptEngineUtils.scriptEngine(extension);
                    ScriptContext scriptContext = new GoogleScriptContext();
                    final Bindings bindings = scriptEngine.createBindings();
                    bindings.put("webPropertyID", webPropertyID);
                    bindings.put(COOKIE_EXPIRES, site.hasProperty(COOKIE_EXPIRES) ? site.getProperty(COOKIE_EXPIRES).getLong() : DEFAULT_COOKIE_EXPIRES);
                    bindings.put("resource", resource);
                    bindings.put("gaMap",renderContext.getRequest().getAttribute("gaMap"));
                    scriptContext.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
                    // The following binding is necessary for Javascript, which doesn't offer a console by default.
                    bindings.put("out", new PrintWriter(scriptContext.getWriter()));
                    scriptEngine.eval(script, scriptContext);
                    StringWriter writer = (StringWriter) scriptContext.getWriter();
                    final String googleAnalyticsScript = writer.toString();
                    if (StringUtils.isNotBlank(googleAnalyticsScript)) {
                        outputDocument.replace(headEndTag.getBegin(), headEndTag.getBegin() + 1,
                                "\n" + AggregateCacheFilter.removeEsiTags(googleAnalyticsScript) + "\n<");
                    }
                    break; // avoid to loop if for any reasons multiple body in the page
                }
                out = outputDocument.toString().trim();
            }
        }
        
        return out;
    }
    
    protected String getResolvedTemplate() throws IOException {
        if (resolvedTemplate == null) {
            resolvedTemplate = WebUtils.getResourceAsString(template);
            if (resolvedTemplate == null) {
                logger.warn("Unable to lookup template at {}", template);
            }
        }
        return resolvedTemplate;
    }
    
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof TemplatePackageRedeployedEvent) {
            resolvedTemplate = null;
        }
    }
    
    public void setScriptEngineUtils(ScriptEngineUtils scriptEngineUtils) {
        this.scriptEngineUtils = scriptEngineUtils;
    }
    public void setTemplate(String template) {
        this.template = template;
    }
    
    class GoogleScriptContext extends SimpleScriptContext {
        private Writer writer = null;

        /**
         * {@inheritDoc}
         */
        @Override
        public Writer getWriter() {
            if (writer == null) {
                writer = new StringWriter();
            }
            return writer;
        }
    }
}
