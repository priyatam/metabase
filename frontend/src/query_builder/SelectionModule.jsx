import React, { Component, PropTypes } from "react";

import Popover from "metabase/components/Popover.jsx";
import Icon from "metabase/components/Icon.jsx";
import SearchBar from './SearchBar.jsx';

import _ from "underscore";
import cx from "classnames";

export default class SelectionModule extends Component {
    constructor(props, context) {
        super(props, context);
        this._expand = this._expand.bind(this);
        this._select = this._select.bind(this);
        this._toggleOpen = this._toggleOpen.bind(this);
        this.onClose = this.onClose.bind(this);
        // a selection module can be told to be open on initialization but otherwise is closed
        var isInitiallyOpen = props.isInitiallyOpen || false;

        this.state = {
            open: isInitiallyOpen,
            expanded: false,
            searchThreshold: 20,
            searchEnabled: false,
            filterTerm: null
        };
    }

    static propTypes = {
        action: PropTypes.func.isRequired,
        display: PropTypes.string.isRequired,
        descriptionKey: PropTypes.string,
        expandFilter: PropTypes.func,
        expandTitle: PropTypes.string,
        isInitiallyOpen: PropTypes.bool,
        items: PropTypes.array,
        remove: PropTypes.func,
        selectedKey: PropTypes.string,
        selectedValue: PropTypes.node,
        parentIndex: PropTypes.number,
        placeholder: PropTypes.string
    };

    static defaultProps = {
        className: ""
    };

    onClose() {
        this.setState({
            open: false,
            expanded: false
        });
    }

    _enableSearch() {
        /*
        not showing search for now
        if(this.props.items.length > this.state.searchThreshold) {
            return true
        } else {
            return false
        }
        */
        return false;
    }

    _toggleOpen() {
        this.setState({
            open: !this.state.open,
            expanded: !this.state.open ? this.state.expanded : false
        });
    }

    _expand() {
        this.setState({
            expanded: true
        });
    }

    _isExpanded() {
        if (this.state.expanded || !this.props.expandFilter) {
            return true;
        }
        // if an item that is normally in the expansion is selected then show the expansion
        for (var i = 0; i < this.props.items.length; i++) {
            var item = this.props.items[i];
            if (this._itemIsSelected(item) && !this.props.expandFilter(item, i)) {
                return true;
            }
        }
        return false;
    }

    _displayCustom(values) {
        var custom = [];
        this.props.children.forEach(function (element) {
            var newElement = element;
            newElement.props.children = values[newElement.props.content];
            custom.push(element);
        });
        return custom;
    }

    _listItems(selection) {
        if (this.props.items) {
            var sourceItems = this.props.items;

            var isExpanded = this._isExpanded();
            if (!isExpanded) {
                sourceItems = sourceItems.filter(this.props.expandFilter);
            }

            var items = sourceItems.map(function (item, index) {
                var display = (item) ? item[this.props.display] || item : item;
                var itemClassName = cx({
                    'SelectionItem' : true,
                    'SelectionItem--selected': selection === display
                });
                var description = null;
                if (this.props.descriptionKey && item && item[this.props.descriptionKey]) {
                    description = (
                        <div className="SelectionModule-description">
                            {item[this.props.descriptionKey]}
                        </div>
                    );
                }
                // if children are provided, use the custom layout display
                return (
                    <li className={itemClassName} onClick={this._select.bind(null, item)} key={index}>
                        <Icon name="check" width="12px" height="12px" />
                        <div className="flex-full">
                            <div className="SelectionModule-display">
                                {display}
                            </div>
                            {description}
                        </div>
                    </li>
                );
            }, this);

            if (!isExpanded && items.length !== this.props.items.length) {
                items.push(
                    <li className="SelectionItem border-top" onClick={this._expand} key="expand">
                        <Icon name="chevrondown" width="12px" height="12px" />
                        <div>
                            <div className="SelectionModule-display">
                                {this.props.expandedTitle || "Advanced..."}
                            </div>
                        </div>
                    </li>
                );
            }

            return items;
        } else {
            return "Sorry. Something went wrong.";
        }
    }

    _select(item) {
        var index = this.props.index;
        // send back the item with the specified action
        if (this.props.action) {
            if (index !== undefined) {
                if (this.props.parentIndex) {
                    this.props.action(item[this.props.selectedKey], index, this.props.parentIndex);
                } else {
                    this.props.action(item[this.props.selectedKey], index);
                }
            } else {
                this.props.action(item[this.props.selectedKey]);
            }
        }
        this._toggleOpen();
    }

    _itemIsSelected(item) {
        return item && _.isEqual(item[this.props.selectedKey], this.props.selectedValue);
    }

    renderPopover(selection) {
        if(this.state.open) {
            var itemListClasses = cx("SelectionItems", {
                'SelectionItems--open': this.state.open,
                'SelectionItems--expanded': this.state.expanded
            });

            var searchBar;
            if(this._enableSearch()) {
                searchBar = <SearchBar onFilter={this._filterSelections} />;
            }

            return (
                <Popover
                    className={"SelectionModule " + this.props.className}
                    onClose={this.onClose}
                >
                    <div className={itemListClasses}>
                        {searchBar}
                        <ul className="SelectionList scroll-show scroll-y">
                            {this._listItems(selection)}
                        </ul>
                    </div>
                </Popover>
            );
        }
    }

    render() {
        var selection;
        this.props.items.forEach(function (item) {
            if (this._itemIsSelected(item)) {
                selection = item[this.props.display];
            }
        }, this);

        var placeholder = selection || this.props.placeholder,
            remove,
            removeable = !!this.props.remove;

        var moduleClasses = cx({
            'SelectionModule': true,
            'selected': selection,
            'removeable': removeable
        });

        if(this.props.remove) {
            remove = (
                <a className="text-grey-2 no-decoration pr1 flex align-center" href="#" onClick={this.props.remove.bind(null, this.props.index)}>
                    <Icon name='close' width="14px" height="14px" />
                </a>
            );
        }

        return (
            <div className={moduleClasses + " " + this.props.className}>
                <div className="SelectionModule-trigger flex align-center">
                    <a className="QueryOption p1 flex align-center" onClick={this._toggleOpen}>
                        {placeholder}
                    </a>
                    {remove}
                </div>
                {this.renderPopover(selection)}
            </div>
        );
    }
}
