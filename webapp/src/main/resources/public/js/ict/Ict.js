import React from "react";
import ReactDOM from "react-dom";

import IctMetadataExtractor from "./IctMetadataExtractor";
import IctModalContainer from "./IctModalContainer";
import TagsBlockDecoder from './TagsBlockDecoder';
import {IntlProvider} from "react-intl";

class Ict {

    constructor(messages, locale) {
        this.mojitoBaseUrl = 'http://localhost:8080/';
        this.messages = messages;
        this.locale = locale;
        this.actionButtons = [];
    }

    activate() {
        this.installInDOM();
        this.addMutationObserver();
        this.addScheduledUpdates();
    }

    setMojitoBaseUrl(mojitoBaseUrl) {
        this.mojitoBaseUrl = mojitoBaseUrl;
        if (this.ictModalContainer) {
            this.ictModalContainer.setMojitoBaseUrl(mojitoBaseUrl);
        }
    }

    setActionButtons(actionButtons) {
        this.actionButtons = actionButtons;
        if (this.ictModalContainer) {
            this.ictModalContainer.setActionButtons(actionButtons);
        }
    }

    getNodesChildOf(node) {
        var nodes = [];

        for (node = node.firstChild; node; node = node.nextSibling) {
            if (node.nodeType === Node.TEXT_NODE) {
                nodes.push(node);
            } else if (this.isElementWithPlaceholder(node)) {
                nodes.push(node);
            } else {
                nodes = nodes.concat(this.getNodesChildOf(node));
            }
        }

        return nodes;
    }

    isElementWithPlaceholder(node) {
        return node.nodeType === Node.ELEMENT_NODE && this.getPlaceholderAttribute(node);
    }

    getPlaceholderAttribute(node) {
        return node.getAttribute('placeholder');
    }

    getStringFromNode(node) {

        var string = node.nodeValue;

        if (string && IctMetadataExtractor.hasPartialMetadata(string)) {
            // the input node doesn't contain the full ict metadata, it should
            // have sibblings that contain the end of the metadata. Usually 
            // this happens when HTML is injected into a message and then the 
            // string is spread accross multiple DOM nodes. In that case, the 
            // full string can be retreive by getting the parent node text 
            // content
            string = node.parentNode.textContent;
        }

        if (this.isElementWithPlaceholder(node)) {
            string = this.getPlaceholderAttribute(node);
        }

        return string;
    }

    wrapNode(node, onClick) {
        var textUnits = IctMetadataExtractor.getTextUnits(this.getStringFromNode(node));

        if (node.nodeType === Node.TEXT_NODE) {
            node = node.parentNode;
        }

        if (!node.classList.contains("mojito-ict-string")) {
           try {
                node.className += " mojito-ict-string";
            } catch (e) {
            }

            node.addEventListener("mouseenter", (e) => {
                e.target.classList.add("mojito-ict-string-active");
            });

            node.addEventListener("mouseleave", (e) => {
                e.target.classList.remove("mojito-ict-string-active");
            });
        }

        node.addEventListener("click", (e) => onClick(e, textUnits));
    }

    installInDOM() {
        var body = document.body || document.getElementsByTagName('body')[0];
        var divIct = document.createElement('div');

        divIct.setAttribute('id', 'mojito-ict');
        // latticehq.com adds forcibly "height: 100%" to the Mojito div, which in turn push the rendering of the lattice
        // app one screen below. Make the Mojito div float to prevent the rendering issue...
        divIct.setAttribute("style", 'float: left;');
        body.insertBefore(divIct, document.body.firstChild);

        ReactDOM.render(
                <IntlProvider locale={this.locale} messages={this.messages}>
                    <IctModalContainer
                        ref={(child) => this.ictModalContainer = child}
                        defaultMojitoBaseUrl={this.mojitoBaseUrl}
                        actionButtons={this.actionButtons}
                        locale={this.locale} />
                </IntlProvider>,
                divIct
                );
    }

    onClickBehavior(e, textUnits) {
        if (e.shiftKey) {
            e.preventDefault();
            e.stopPropagation();
            this.ictModalContainer.showModal(textUnits);
        }
    }

    processNode(node) {
        var hasMetaData = IctMetadataExtractor.hasMetadata(this.getStringFromNode(node));

        if (hasMetaData) {
            this.wrapNode(node, this.onClickBehavior.bind(this));
            this.removeTagsBlockFromNode(node);
        }
    }

    removeTagsBlockFromNode(node) {
        if (this.isElementWithPlaceholder(node)) {
            node.setAttribute('placeholder', TagsBlockDecoder.removeTagsBlock(node.getAttribute('placeholder')));
        } else if (node.textContent) {
            node.textContent = TagsBlockDecoder.removeTagsBlock(node.textContent);
        }
    }

    addMutationObserver() {
        var observer = new MutationObserver((mutationRecords) => {
            mutationRecords.forEach((r) => {
                if (!r.target.classList.contains('mojito-ict')) {
                    this.needsRefresh = true;
                }
            });
        });

        var observerConfig = {childList: true, subtree: true, attribute: true};
        var targetNode = document.body;
        observer.observe(targetNode, observerConfig);
    }

    addScheduledUpdates() {
        this.needsRefresh = true;
        setInterval(() => {
            if (this.needsRefresh) {
                this.needsRefresh = false;
                this.getNodesChildOf(window.document).forEach((node) => {
                    this.processNode(node);
                });
            }
        }, 200);
    }
}

export default Ict;